package vine.providers

import vine.Application.TrackMsg
import vine.config.Config
import vine.config.ConfigModel.Feed
import vine.conlimiter.ConnectionsLimiter
import vine.counters.Counters
import vine.providers.Exceptions._
import vine.filters._
import sttp.client3
import sttp.client3._
import sttp.client3.httpclient.zio.{SttpClient, send}
import sttp.model._
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import vine.helpers.Conversions._
import vine.providers.MusicStoreDataProvider.{SttpBytesRequest, providerBasicRequest}
import vine.ui.consoleprogress.ConsoleProgress
import vine.ui.consoleprogress.ConsoleProgress.BucketRef
import zio.logging._

import java.time.LocalDate

final case class PageSummary(goodTracks: Int, brokenTracks : Int, pagerOpt: Option[Pager]) {
  val totalTracks = goodTracks + brokenTracks
}

trait MusicStoreDataProvider {

    def host : String

    protected val consoleProgress   : ConsoleProgress.Service
    protected val sttpClient        : SttpClient.Service

    protected val trackRequest: RequestT[Empty, Either[String, Array[Byte]], Any] =
        providerBasicRequest.response(asByteArray)

    def processTracks(
      feed          : Feed,
      dateFrom      : LocalDate,
      dateTo        : LocalDate,
      queue         : Queue[TrackMsg])
    : ZIO[Counters with ConsoleProgress with Clock with Logging with ConnectionsLimiter with ConsoleProgress,
      Throwable, Unit]
    =
     for {
        firstSummaryP   <- Promise.make[Throwable, PageSummary]
        firstBucketP    <- Promise.make[Throwable, BucketRef]

        firstFiber      <- processTracklistPage(feed, dateFrom, dateTo, 1, queue, firstBucketP, firstSummaryP).fork
        firstSummary    <- firstSummaryP.await

        _ <- firstSummary match {

              case PageSummary(0, 0, None) => for {
                buckets <- ConsoleProgress.initializeBar(feed.name, List(1))
                _       <- firstBucketP.succeed(buckets.head)
                _       <- ConsoleProgress.completeBar(feed.name)
              } yield ()

              case PageSummary(_, _, None) => for {
                  buckets <- ConsoleProgress.initializeBar(feed.name, List(firstSummary.totalTracks))
                  _       <- handleBrokenTracks(firstSummary, buckets.head, feed, dateFrom, dateTo, 1)
                  _       <- firstBucketP.succeed(buckets.head)
              } yield ()

              case PageSummary(_, _, Some(Pager(_, last))) => for {
                  lastSummaryP    <- Promise.make[Throwable, PageSummary]
                  lastBucketP     <- Promise.make[Throwable, BucketRef]

                  lastFiber   <- processTracklistPage(feed, dateFrom, dateTo, last, queue, lastBucketP, lastSummaryP).fork
                  lastSummary <- lastSummaryP.await
                  _           <- log.warn(s"Got empty last page of ${feed.name}. Beatport 10K bug?")
                                  .when(lastSummary.totalTracks == 0)

                  bucketSizes =  (1 until last).map(_ => firstSummary.totalTracks) :+ lastSummary.totalTracks
                  buckets     <- ConsoleProgress.initializeBar(feed.name, bucketSizes.toList).map(_.toVector)

                  _           <- handleBrokenTracks(firstSummary, buckets.head, feed, dateFrom, dateTo, pageNum = 1)
                  _           <- handleBrokenTracks(lastSummary, buckets.last, feed, dateFrom, dateTo, pageNum = last)
                  _           <- firstBucketP.succeed(buckets.head)
                  _           <- lastBucketP.succeed(buckets.last)

                  remainingPg =  (2 until last).toList
                  _           <- ZIO.foreachParN_(8)(remainingPg) { pg =>
                                    processIntermediatePage(feed, dateFrom, dateTo, pg, queue, buckets(pg-1))
                                  }

                  _           <- lastFiber.join
              } yield ()
          }

        _   <- firstFiber.join
     } yield ()

  def processIntermediatePage(
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    queue            : Queue[TrackMsg],
    bucketRef        : BucketRef
  ): ZIO[Clock with Logging with ConnectionsLimiter with ConsoleProgress with Counters, Throwable, Unit] =
    for {
      bucketP     <- Promise.make[Throwable, BucketRef]
      summaryP    <- Promise.make[Throwable, PageSummary]
      fiber       <- processTracklistPage(feed, dateFrom, dateTo, pageNum, queue, bucketP, summaryP).fork
      summary     <- summaryP.await
      _           <- handleBrokenTracks(summary, bucketRef, feed, dateFrom, dateTo, pageNum) *>
                     handleEmptyIntermediatePage(summary, bucketRef, feed, dateFrom, dateTo, pageNum)
      _           <- bucketP.succeed(bucketRef)
      _           <- fiber.join
    } yield ()

  private def handleBrokenTracks(
    pageSummary : PageSummary, bucketRef: BucketRef, feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, pageNum : Int) =
  {
    if (pageSummary.brokenTracks > 0) {
      for {
        _         <- ConsoleProgress.failMany(bucketRef, pageSummary.brokenTracks)
        url       <- ZIO.succeed(buildPageUri(host, feed.urlTemplate, dateFrom, dateTo, pageNum))
        logString =  s"Broken tracks detected on $url"
        _         <- log.warn(logString)
      } yield ()
    } else ZIO.succeed()
  }

  private def handleEmptyIntermediatePage(
    pageSummary : PageSummary, bucketRef: BucketRef, feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, pageNum : Int) = {
    if (pageSummary.totalTracks == 0) {
      for {
        _         <- ConsoleProgress.failAll(bucketRef)
        url       <- ZIO.succeed(buildPageUri(host, feed.urlTemplate, dateFrom, dateTo, pageNum))
        logString =  s"Empty intermediate page (Beatport 10K bug?): $url"
        _         <- log.warn(logString)
      } yield ()
    } else ZIO.succeed()
  }

    def processTracklistPage(
      feed             : Feed,
      dateFrom         : LocalDate,
      dateTo           : LocalDate,
      pageNum          : Int,
      queue            : Queue[TrackMsg],
      inBucket         : Promise[Throwable, BucketRef],
      outSummary       : Promise[Throwable, PageSummary])
    : ZIO[Clock with Logging with ConnectionsLimiter with ConsoleProgress with Counters, Throwable, Unit]

    private[providers] def buildPageUri(
      host        : String,
      urlTemplate : String,
      dateFrom    : LocalDate,
      dateTo      : LocalDate,
      page        : Int = 1)
    : Either[Throwable, Uri] =
    {
        val pageParam = if (page != 1) s"&page=$page" else ""

        val uriStr =
            host ++
              urlTemplate
                .replace("{0}", dateFrom.toString)
                .replace("{1}", dateTo.toString) ++
              pageParam

        Uri.parse(uriStr)
          .left.map(msg => InternalConfigurationError(s"Не удалось приготовить ссылку по шаблону $urlTemplate, $msg"))
    }
}

object MusicStoreDataProvider {

  type SttpBytesRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

  protected val providerBasicRequest: RequestT[Empty, Either[String, String], Any] = basicRequest
    .header(HeaderNames.UserAgent,
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  def fetchWithTimeoutAndRetry(sttpClient : SttpClient.Service, uri : Uri)
  : ZIO[ConnectionsLimiter with Logging with Clock, Throwable, Array[Byte]] = {

    def attempt(req : SttpBytesRequest) = for {
      resp  <- sttpClient.send(req).timeout(2.minutes)
      body  <- resp match {
        case None => ZIO.fail(ServiceUnavailable("Timeout", uri))

        case Some(Response(Left(msg),code,text,_,_,_)) =>
          ZIO.fail(ServiceUnavailable(s"Status code $code: $text | $msg", uri))

        case Some(Response(Right(body),_,_,headers,_,_)) =>
          val contentLength = headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
          val lengthValid = contentLength.fold(true)(l => body.length == l)
          if (lengthValid) ZIO.succeed(body)
          else ZIO.fail(BadContentLength(s"Bad content length", uri))
      }
    } yield body

    val retries = 10
    val spaced = 10.seconds

    for {
      req       <- ZIO(providerBasicRequest.get(uri).response(asByteArray))
      schedule  =  (Schedule.recurs(retries) && Schedule.spaced(spaced))
                    .tapOutput { case (n, _) => log.warn(s"Retrying $uri").when(n < retries) }
      body      <- ConnectionsLimiter.withPermit(uri) {
                    log.trace(s"$uri") *>
                    attempt(req).tapError { e => log.warn(s"Got ${e.toString} while sending $uri") }
                  }.retry(schedule)
    } yield body
  }
}
