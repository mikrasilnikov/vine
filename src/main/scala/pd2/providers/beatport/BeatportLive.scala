package pd2.providers.beatport

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pager, TrackDto}
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import sttp.client3.asByteArray
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.Uri
import zio.clock.Clock
import zio.logging.Logging
import zio.{Promise, Semaphore, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class BeatportLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service,
  providerSemaphore   : Semaphore,
  globalSemaphore     : Semaphore)
  extends Beatport.Service {

  val beatportHost = "https://www.beatport.com"

  def processTracklistPage[R, E <: Throwable](
    feed: Feed,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    pageNum: Int,
    filter: TrackFilter,
    processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pageProgressItem: ProgressItem,
    pagePromiseOption: Option[Promise[Throwable, Option[Pager]]])
  : ZIO[R with FilterEnv with Clock with Logging, Throwable, Unit] = {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, pageProgressItem, pageNum)
                                  .tapError(e => pagePromiseOption.fold(ZIO.succeed())(promise => promise.fail(e).unit))
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page.pager).unit)

      filteredTracksWithDtos  <- ZIO.filter(page.tracks.map(st => (st, st.toTrackDto(feed.name)))) { case (_, dto) => filter.check(dto) }
      tracksProgress          <- consoleProgress.acquireProgressItems(feed.name, filteredTracksWithDtos.length)
      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos zip tracksProgress) { case ((t, dto), p) =>
                                (for {
                                  downloadResult <- downloadTrack(t.previewUrl, p)
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

  private def getTracklistWebPage(feed: Feed, dateFrom: LocalDate, dateTo: LocalDate, progress : ProgressItem, page: Int = 1)
  : ZIO[Clock with Logging, Throwable, BeatportPage] = {
    for {
      pageUri   <- buildPageUri(beatportHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- download(pageUri, progress).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- BeatportPage.parse(pageResp).toZio
    } yield page
  }


}

object BeatportLive {
  def makeLayer(maxConcurrentConnections : Int): ZLayer[Config with ConsoleProgress with SttpClient, Nothing, Beatport] =
    ZLayer.fromServicesM[Config.Service, ConsoleProgress.Service, SttpClient.Service, Any, Nothing, Beatport.Service] {
      case (config, consoleProgress, sttpClient) => for {
        providerSemaphore <- Semaphore.make(maxConcurrentConnections)
      } yield BeatportLive(consoleProgress, sttpClient, providerSemaphore, config.globalConnSemaphore)
    }
}