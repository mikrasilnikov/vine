package pd2.providers.beatport

import pd2.Application.TrackMsg
import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.PageSummary
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
import sttp.client3.httpclient.zio.SttpClient
import zio.clock.Clock
import zio.logging.Logging
import zio.{Promise, Queue, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class BeatportLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service)
  extends Beatport.Service {

  val beatportHost = "https://www.beatport.com"

  override def processTracklistPage(
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    queue            : Queue[TrackMsg],
    inBucket         : Promise[Throwable, BucketRef],
    outSummary       : Promise[Throwable, PageSummary])
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, Unit] = {
    for {
      page    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum).tapError(e => outSummary.fail(e))
      _       <- outSummary.succeed(PageSummary(page.tracks.length, page.pager))
      bucket  <- inBucket.await
      msgs    =  page.tracks.map(st => TrackMsg(st.toTrackDto(feed.name), bucket))
      _       <- queue.offerAll(msgs)
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