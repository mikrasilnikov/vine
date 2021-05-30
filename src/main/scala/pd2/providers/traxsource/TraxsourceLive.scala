package pd2.providers.traxsource

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Pd2Exception.{InternalConfigurationError, ServiceUnavailable, TraxsourceBadContentLength}
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pd2Exception, TrackDto}
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import sttp.client3
import sttp.client3.httpclient.zio.SttpClient
import sttp.client3.{RequestT, asByteArray, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.clock.Clock
import zio.duration.durationInt
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
  : ZIO[R with FilterEnv with Clock, Throwable, Unit] =
  {
    for {
      firstPageProgress <- consoleProgress.acquireProgressItem(feed.name)
      firstPagePromise  <- Promise.make[Nothing, TraxsourcePage]
      firstPageFiber    <- processTracklistPage(feed, dateFrom, dateTo, 1, filter, processTrack,
                                                firstPageProgress, Some(firstPagePromise)).fork
      firstPage         <- firstPagePromise.await
      remainingProgress <- consoleProgress.acquireProgressItems(feed.name, firstPage.remainingPages.length)
      _                 <- ZIO.foreachParN_(8)(firstPage.remainingPages zip remainingProgress) { case (page, p) =>
                              processTracklistPage(feed, dateFrom, dateTo, page, filter, processTrack, p, None)
                          }
      _                 <- firstPageFiber.join
    } yield ()
  }

  private def processTracklistPage[R, E <: Throwable](
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    filter           : TrackFilter,
    processTrack     : (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pageProgressItem : ProgressItem,
    pagePromiseOption: Option[Promise[Nothing, TraxsourcePage]])
  : ZIO[R with FilterEnv with Clock, Throwable, Unit] =
  {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum)
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page).unit)
      serviceTracks           <- getServiceData(page.trackIds, feed.name)
      _                       <- consoleProgress.completeProgressItem(pageProgressItem)
      filteredTracksWithDtos  <- ZIO.filter(serviceTracks.map(st => (st, st.toTrackDto))) { case (_, dto) => filter.check(dto) }
      tracksProgress          <- consoleProgress.acquireProgressItems(feed.name, filteredTracksWithDtos.length)
      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos zip tracksProgress) { case ((t, dto), p) =>
                                  for {
                                    _ <- downloadTrack(t.mp3Url)
                                          .flatMap(bytes => processTrack(dto, bytes) *> filter.done(dto))
                                          .whenM(filter.checkBeforeProcessing(dto))
                                    _ <- consoleProgress.completeProgressItem(p)
                                  } yield ()
                                }
    } yield ()
  }

  private def getTracklistWebPage(feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
  : ZIO[Clock, Pd2Exception, TraxsourcePage] =
  {
    for {
      pageReq   <- buildPageRequest(traxsourceHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- performRequest(pageReq).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourcePage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String)
  : ZIO[Clock, Pd2Exception, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceReq  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- performRequest(serviceReq).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      tracks      <- TraxsourceServiceTrack.fromServiceResponse(serviceResp, feed).toZio
    } yield tracks
  }

  private def downloadTrack(trackUri : Uri) : ZIO[Clock, Pd2Exception, Array[Byte]] =
  {
    for {
      request <- ZIO.succeed(providerBasicRequest.get(trackUri).response(asByteArray))
      bytes   <- performRequest(request)
    } yield bytes
  }

  private def buildTraxsourceServiceRequest(trackIds : List[Int]) : Either[Pd2Exception, SttpRequest] =
  {
    val uriStr = s"https://w-static.traxsource.com/scripts/playlist.php?tracks=${trackIds.mkString(",")}"

    val eitherRequest = for {
      uri <- Uri.parse(uriStr)
    } yield
      providerBasicRequest
        .get(uri)
        .response(asByteArray)

    eitherRequest.left.map(msg => InternalConfigurationError(msg))
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