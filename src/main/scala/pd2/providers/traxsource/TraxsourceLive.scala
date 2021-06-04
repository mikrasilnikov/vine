package pd2.providers.traxsource

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Exceptions.{BadContentLength, InternalConfigurationError, ServiceUnavailable}
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.TrackDto
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import sttp.client3
import sttp.client3.httpclient.zio.SttpClient
import sttp.client3.{RequestT, asByteArray, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.clock.Clock
import zio.console.putStrLn
import zio.duration.durationInt
import zio.logging.Logging
import zio.{Promise, Schedule, Semaphore, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class TraxsourceLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service,
  providerSemaphore   : Semaphore,
  globalSemaphore     : Semaphore)
  extends Traxsource.Service
{
  private val traxsourceHost = "https://www.traxsource.com"

  override def processTracks[R , E <: Throwable](
      feed          : Feed,
      dateFrom      : LocalDate,
      dateTo        : LocalDate,
      filter        : TrackFilter,
      processTrack  : (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
  : ZIO[R with FilterEnv with ConsoleProgress with Clock with Logging, Throwable, Unit] =
  {
    for {
      firstPagePromise  <- Promise.make[Throwable, TraxsourcePage]
      firstPageFiber    <- processTracklistPage(feed, dateFrom, dateTo, 1, filter, processTrack,
                                                None, Some(firstPagePromise)).fork
      firstPage         <- firstPagePromise.await
      remainingProgress <- consoleProgress.acquireProgressItems(feed.name, firstPage.remainingPages.length)
      _                 <- ZIO.foreachParN_(8)(firstPage.remainingPages zip remainingProgress) { case (page, p) =>
                              processTracklistPage(feed, dateFrom, dateTo, page, filter, processTrack, Some(p), None)
                          }
      _                 <- firstPageFiber.join
      // Если при обработке фида вообще ничего не скачивалось, то ProgressBar будет стоять на 0%.
      // Если взять 1 ProgressItem и сразу отметить его как законченный, то ProgressBar будет показывать 100%
      lastProgress      <- ConsoleProgress.acquireProgressItem(feed.name)
      _                 <- ConsoleProgress.completeProgressItem(lastProgress)
    } yield ()
  }

  private def processTracklistPage[R, E <: Throwable](
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    filter           : TrackFilter,
    processTrack     : (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pageProgressItem : Option[ProgressItem],
    pagePromiseOption: Option[Promise[Throwable, TraxsourcePage]])
  : ZIO[R with FilterEnv with ConsoleProgress with Clock with Logging, Throwable, Unit] =
  {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, None, pageNum)
                                  .tapError(e => pagePromiseOption.fold(ZIO.succeed())(promise => promise.fail(e).unit))
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page).unit)
      serviceTracks           <- if (page.trackIds.nonEmpty) getServiceData(page.trackIds, feed.name, pageProgressItem)
                                 else ZIO.succeed(List())
      _                       <- if (pageProgressItem.isDefined) consoleProgress.completeProgressItem(pageProgressItem.get)
                                  else ZIO.succeed()
      filteredTracksWithDtos  <- ZIO.filter(serviceTracks.map(st => (st, st.toTrackDto))) { case (_, dto) => filter.check(dto) }
      tracksProgress          <- consoleProgress.acquireProgressItems(feed.name, filteredTracksWithDtos.length)
      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos zip tracksProgress) { case ((t, dto), p) =>
                                  (for {
                                    dataOpt <- downloadTrack(t.mp3Url, p)
                                    _ <- dataOpt match {
                                      case Some(bytes) =>
                                          processTrack(dto, bytes) *>
                                          filter.done(dto) *>
                                          consoleProgress.completeProgressItem(p)
                                      case None =>
                                          filter.done(dto) *>
                                          consoleProgress.failProgressItem(p)
                                    }
                                  } yield ()).whenM(filter.checkBeforeProcessing(dto)
                                              .tap(b => ZIO.unless(b)(consoleProgress.completeProgressItem(p))))
                                }
    } yield ()
  }

  private def getTracklistWebPage(
    feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, progressOpt : Option[ProgressItem], page: Int = 1)
  : ZIO[ConsoleProgress with Clock with Logging, Throwable, TraxsourcePage] =
  {
    for {
      uri       <- buildPageUri(traxsourceHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- download(uri, progressOpt).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourcePage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String, progressItem : Option[ProgressItem])
  : ZIO[ConsoleProgress with Clock with Logging, Throwable, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceUri  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- download(serviceUri, progressItem).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      tracks      <- TraxsourceServiceTrack.fromServiceResponse(serviceResp, feed).toZio
    } yield tracks
  }

  private def downloadTrack(trackUri: Uri, progressItem : ProgressItem)
  : ZIO[ConsoleProgress with Clock with Logging, Throwable, Option[Array[Byte]]] =
  {
    download(trackUri, Some(progressItem))
      .map(Some(_))
      .orElseSucceed(None)
  }

  private def buildTraxsourceServiceRequest(trackIds : List[Int]) : Either[Throwable, Uri] =
  {
    val uriStr = s"https://w-static.traxsource.com/scripts/playlist.php?tracks=${trackIds.mkString(",")}"
    Uri.parse(uriStr).left.map(msg => InternalConfigurationError(msg))
  }
}

object TraxsourceLive {
  def makeLayer(maxConcurrentConnections : Int): ZLayer[Config with ConsoleProgress with SttpClient, Nothing, Traxsource] =
    ZLayer.fromServicesM[Config.Service, ConsoleProgress.Service, SttpClient.Service, Any, Nothing, Traxsource.Service] {
    case (config, consoleProgress, sttpClient) => for {
      providerSemaphore <- Semaphore.make(maxConcurrentConnections)
    } yield TraxsourceLive(consoleProgress, sttpClient, providerSemaphore, config.globalConnSemaphore)
  }
}