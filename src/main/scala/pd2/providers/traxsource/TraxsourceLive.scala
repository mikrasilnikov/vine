package pd2.providers.traxsource

import pd2.Application.TrackMsg
import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.counters.Counters
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Exceptions.InternalConfigurationError
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{PageSummary, Pager, TrackDto}
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
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
  private val traxsourceHost = "https://www.traxsource.com"

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
      _       <- outSummary.succeed(PageSummary(page.trackIds.length, page.pager))
      bucket  <- inBucket.await
      _       <- processBrokenTracksIfAny(page, feed, dateFrom, dateTo, pageNum, bucket)
      tracks  <- if (page.trackIds.nonEmpty) getServiceData(page.trackIds, feed.name) else ZIO.succeed(List())
      msgs    =  tracks.map(st => TrackMsg(st.toTrackDto, bucket))
      _       <- queue.offerAll(msgs) *> Counters.modify(s"${feed.name}_M", msgs.length)
    } yield ()
  }

  private def processBrokenTracksIfAny(
    page : TraxsourcePage, feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, pageNum : Int, bucket : BucketRef) =
  {
    if (page.brokenTracks > 0) {
      for {
        _         <- ZIO.foreach_(0 until page.brokenTracks)(_ =>
                      ConsoleProgress.failOne(bucket) *> Counters.modify(s"${feed.name}_P", -1))
        url       =  buildPageUri(traxsourceHost, feed.urlTemplate, dateFrom, dateTo, pageNum)
        logString =  s"Broken tracks detected on $url"
        _         <- log.warn(logString)
      } yield ()
    } else ZIO.succeed()
  }

  private def getTracklistWebPage(
    feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, TraxsourcePage] =
  {
    for {
      uri       <- buildPageUri(traxsourceHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- download(uri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourcePage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceUri  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- download(serviceUri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
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