package pd2.providers.beatport

import pd2.Application.TrackMsg
import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.counters.Counters
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.{MusicStoreDataProvider, PageSummary}
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
import sttp.client3.httpclient.zio.SttpClient
import zio.clock.Clock
import zio.logging.{Logging, log}
import zio._

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class BeatportLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service)
  extends Beatport.Service {

  val host = "https://www.beatport.com"

  override def processTracklistPage(
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    queue            : Queue[TrackMsg],
    inBucket         : Promise[Throwable, BucketRef],
    outSummary       : Promise[Throwable, PageSummary])
  : ZIO[Clock with Logging with ConnectionsLimiter with Counters, Throwable, Unit] = {
    for {
      page    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum).tapError(e => outSummary.fail(e))
      _       <- outSummary.succeed(PageSummary(goodTracks = page.tracks.length, brokenTracks = 0, page.pager))
      bucket  <- inBucket.await
      msgs    =  page.tracks.map(st => TrackMsg(st.toTrackDto(feed.name), bucket))
      _       <- queue.offerAll(msgs) *> Counters.modify(feed.name, msgs.length)
    } yield ()
  }

  private def getTracklistWebPage(feed: Feed, dateFrom: LocalDate, dateTo: LocalDate, page: Int = 1)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, BeatportPage] = {
    import MusicStoreDataProvider._
    for {
      pageUri   <- buildPageUri(host, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- fetchWithTimeoutAndRetry(sttpClient, pageUri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
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