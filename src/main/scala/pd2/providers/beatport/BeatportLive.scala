package pd2.providers.beatport

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pager, TrackDto}
import pd2.ui.consoleprogress.ConsoleProgress
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.Uri
import zio.clock.Clock
import zio.logging.Logging
import zio.{Promise, Semaphore, ZIO, ZLayer}
import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class BeatportLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service)
  extends Beatport.Service {

  val beatportHost = "https://www.beatport.com"

  def processTracklistPage[R, E <: Throwable](
    feed: Feed,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    pageNum: Int,
    filter: TrackFilter,
    processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pagePromiseOption: Option[Promise[Throwable, Option[Pager]]])
  : ZIO[
    R with FilterEnv with Clock with Logging with ConnectionsLimiter,
    Throwable, Unit] = {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum)
                                  .tapError(e => pagePromiseOption.fold(ZIO.succeed())(promise => promise.fail(e).unit))
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page.pager).unit)
      filteredTracksWithDtos  <- ZIO.filter(page.tracks.map(st => (st, st.toTrackDto(feed.name)))) { case (_, dto) => filter.check(dto) }
      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos) { case (t, dto) =>
                                (for {
                                  downloadResult <- downloadTrack(t.previewUrl)
                                  _ <- downloadResult match {
                                      case TrackDownloadResult.Success(bytes) =>
                                          processTrack(dto, bytes) *>
                                          filter.done(dto)
                                      case TrackDownloadResult.Failure =>
                                          filter.done(dto)
                                      case TrackDownloadResult.Skipped =>
                                          filter.done(dto)
                                  }
                                } yield ()).whenM(filter.checkBeforeProcessing(dto))
                              }
    } yield ()
  }

  private def getTracklistWebPage(feed: Feed, dateFrom: LocalDate, dateTo: LocalDate, page: Int = 1)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, BeatportPage] = {
    for {
      pageUri   <- buildPageUri(beatportHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- download(pageUri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- BeatportPage.parse(pageResp).toZio
    } yield page
  }
}

object BeatportLive {
  def makeLayer: ZLayer[Config with ConsoleProgress with SttpClient, Nothing, Beatport] =
    ZLayer.fromServices[ConsoleProgress.Service, SttpClient.Service, Beatport.Service] {
      (consoleProgress, sttpClient) => BeatportLive(consoleProgress, sttpClient)
    }
}