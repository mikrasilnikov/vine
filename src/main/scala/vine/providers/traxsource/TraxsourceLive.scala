package vine.providers.traxsource

import vine.Application.TrackMsg
import vine.config.Config
import vine.config.ConfigModel._
import vine.conlimiter.ConnectionsLimiter
import vine.counters.Counters
import vine.helpers.Conversions.EitherToZio
import vine.providers.Exceptions.InternalConfigurationError
import vine.providers.PageSummary
import vine.ui.consoleprogress.ConsoleProgress
import vine.ui.consoleprogress.ConsoleProgress.BucketRef
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.Uri
import zio.clock.Clock
import zio.logging.{Logging, log}
import zio.{Promise, Queue, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class TraxsourceLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service)
  extends Traxsource.Service
{
  val host = "https://www.traxsource.com"

  override def processTracklistPage(
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    queue            : Queue[TrackMsg],
    inBucket         : Promise[Throwable, BucketRef],
    outSummary       : Promise[Throwable, PageSummary])
  : ZIO[Clock with Logging with ConnectionsLimiter with ConsoleProgress with Counters, Throwable, Unit] =
  {
    for {
      page    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum).tapError(e => outSummary.fail(e))
      _       <- outSummary.succeed(PageSummary(page.trackIds.length, page.brokenTracks, page.pager))
      bucket  <- inBucket.await
      tracks  <- if (page.trackIds.nonEmpty) getServiceData(page.trackIds, feed.name) else ZIO.succeed(List())
      msgs    =  tracks.map(st => TrackMsg(st.toTrackDto, bucket))
      _       <- queue.offerAll(msgs) *> Counters.modify(feed.name, msgs.length)
    } yield ()
  }

  private def getTracklistWebPage(
    feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, TraxsourcePage] =
  {
    import vine.providers.MusicStoreDataProvider._
    for {
      uri       <- buildPageUri(host, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- fetchWithTimeoutAndRetry(sttpClient, uri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourcePage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, List[TraxsourceServiceTrack]] =
  {
    import vine.providers.MusicStoreDataProvider._
    for {
      serviceUri  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- fetchWithTimeoutAndRetry(sttpClient, serviceUri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
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
  def makeLayer : ZLayer[Config with ConsoleProgress with SttpClient, Nothing, Traxsource] =
    ZLayer.fromServices[ConsoleProgress.Service, SttpClient.Service, Traxsource.Service] {
      (consoleProgress, sttpClient) => TraxsourceLive(consoleProgress, sttpClient)
  }
}