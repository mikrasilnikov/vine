package pd2.providers.traxsource

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Exceptions.{BadContentLength, InternalConfigurationError, ServiceUnavailable}
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pager, TrackDto}
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

  def processTracklistPage[R, E <: Throwable](
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    filter           : TrackFilter,
    processTrack     : (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pageProgressItem : ProgressItem,
    pagePromiseOption: Option[Promise[Throwable, Option[Pager]]])
  : ZIO[R with FilterEnv with Clock with Logging, Throwable, Unit] =
  {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, pageProgressItem, pageNum)
                                  .tapError(e => pagePromiseOption.fold(ZIO.succeed())(promise => promise.fail(e).unit))
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page.pager).unit)
      serviceTracks           <- if (page.trackIds.nonEmpty) getServiceData(page.trackIds, feed.name, pageProgressItem)
                                 else ZIO.succeed(List())

      filteredTracksWithDtos  <- ZIO.filter(serviceTracks.map(st => (st, st.toTrackDto))) { case (_, dto) => filter.check(dto) }
      tracksProgress          <- consoleProgress.acquireProgressItems(feed.name, filteredTracksWithDtos.length)
      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos zip tracksProgress) { case ((t, dto), p) =>
                                  (for {
                                    downloadResult <- downloadTrack(t.mp3Url, p)
                                    _ <- downloadResult match {
                                      case TrackDownloadResult.Success(bytes) =>
                                        processTrack(dto, bytes) *>
                                          filter.done(dto) *>
                                          consoleProgress.completeProgressItem(p)
                                      case TrackDownloadResult.Failure =>
                                        filter.done(dto) *>
                                          consoleProgress.failProgressItem(p)
                                      case TrackDownloadResult.Skipped =>
                                        filter.done(dto) *>
                                          consoleProgress.completeProgressItem(p)
                                    }
                                  } yield ()).whenM(filter.checkBeforeProcessing(dto)
                                              .tap(b => ZIO.unless(b)(consoleProgress.completeProgressItem(p))))
                                }
      _                       <- consoleProgress.completeProgressItem(pageProgressItem)
    } yield ()
  }

  private def getTracklistWebPage(
    feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, progress : ProgressItem, page: Int = 1)
  : ZIO[Clock with Logging, Throwable, TraxsourcePage] =
  {
    for {
      uri       <- buildPageUri(traxsourceHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- download(uri, progress).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourcePage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String, progressItem : ProgressItem)
  : ZIO[Clock with Logging, Throwable, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceUri  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- download(serviceUri, progressItem).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      tracks      <- TraxsourceServiceTrack.fromServiceResponse(serviceResp, feed).toZio
    } yield tracks
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