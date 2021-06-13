package pd2.providers

import pd2.Application.TrackMsg
import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.counters.Counters
import pd2.providers.Exceptions._
import pd2.providers.filters._
import sttp.client3
import sttp.client3._
import sttp.client3.httpclient.zio.SttpClient
import sttp.model._
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import pd2.helpers.Conversions._
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
import zio.logging._
import java.time.LocalDate

final case class PageSummary(goodTracks: Int, brokenTracks : Int, pagerOpt: Option[Pager]) {
  val totalTracks = goodTracks + brokenTracks
}

trait MusicStoreDataProvider {

    type SttpBytesRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

    def host : String

    protected val consoleProgress   : ConsoleProgress.Service
    protected val sttpClient        : SttpClient.Service

    protected val providerBasicRequest: RequestT[Empty, Either[String, String], Any] = basicRequest
      .header(HeaderNames.UserAgent,
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
      .readTimeout(concurrent.duration.Duration(30, concurrent.duration.SECONDS))

    protected val trackRequest: RequestT[Empty, Either[String, Array[Byte]], Any] =
        providerBasicRequest.response(asByteArray)

    def processTracks(
      feed          : Feed,
      dateFrom      : LocalDate,
      dateTo        : LocalDate,
      queue         : Queue[TrackMsg],
      completionP   : Promise[Nothing, Unit]) // Signals to consumers that publishing to queue is finished.
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
        _   <- completionP.succeed()
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

    /**
     * Скачивает данные по ссылке. Использует таймаут на запрос, не-200 коды считаются ошибками.
     * Делает 10 повторных попыток при любых ошибках. Если передан progressItem, выставляет его
     * в состояние InProgress (анимация /-\|) при первой повторной попытке. После 10 неудачных
     * попыток возващает последнюю ошибку.
     */
    protected def download(uri : Uri)
    : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, Array[Byte]] =
    {
        def send(req : SttpBytesRequest) : ZIO[Clock, Throwable, Array[Byte]] = for {
            resp        <- sttpClient.send(req).timeoutFail(ServiceUnavailable("Timeout", uri))(5.minutes)
            data        <- resp.body.toZio.mapError(err => ServiceUnavailable(s"Status code ${resp.code.code}, $err", uri))
            lengthOpt   =  resp.headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
            _           <- ZIO.fail(BadContentLength("Bad content length", uri))
                            .unless(lengthOpt.forall(_ == data.length))
        } yield data

        for {
            req             <- ZIO.effect(providerBasicRequest.get(uri).response(asByteArray))
            schedule        =  Schedule.recurs(10) && Schedule.spaced(5.seconds)
            resp            <- ConnectionsLimiter.withPermit(uri) {
                                    log.trace(s"$uri") *>
                                    send(req).tapError { e => log.warn(s"Retrying\n$uri\n(failed with ${e.toString})") }
                                }
                                .retry(schedule)
                                .tapError(e => log.warn(s"Could not download $uri, error: $e"))
        } yield resp
    }
}
