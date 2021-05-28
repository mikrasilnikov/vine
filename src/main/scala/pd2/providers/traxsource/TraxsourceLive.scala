package pd2.providers.traxsource

import pd2.config.ConfigDescription.Feed.TraxsourceFeed
import pd2.config.FilterTag
import pd2.data.{Pd2Database, DatabaseService}
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Pd2Exception.{InternalConfigurationError, ServiceUnavailable, TraxsourceBadContentLength}
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pd2Exception, TrackDto, TraxsourceServiceTrack, TraxsourceWebPage}
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
  connectionsSemaphore: Semaphore)
  extends Traxsource.Service
{
  type SttpRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

  private val traxsourceBasicRequest = basicRequest
    .header(HeaderNames.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
    .readTimeout(concurrent.duration.Duration(30, concurrent.duration.SECONDS))

  override def processTracks[R , E <: Throwable](
      feed          : TraxsourceFeed,
      dateFrom      : LocalDate,
      dateTo        : LocalDate,
      filter        : TrackFilter,
      processTrack  : (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
  : ZIO[R with FilterEnv with Clock, Throwable, Unit] =
  {
    for {
      firstPageProgress <- consoleProgress.acquireProgressItem(feed.name)
      firstPagePromise  <- Promise.make[Nothing, TraxsourceWebPage]
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
    feed             : TraxsourceFeed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    filter           : TrackFilter,
    processTrack     : (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pageProgressItem : ProgressItem,
    pagePromiseOption: Option[Promise[Nothing, TraxsourceWebPage]])
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

  private def getTracklistWebPage(feed : TraxsourceFeed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
  : ZIO[Clock, Pd2Exception, TraxsourceWebPage] =
  {
    for {
      pageReq   <- buildTraxsourcePageRequest(feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- performTraxsourceRequest(pageReq).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourceWebPage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String)
  : ZIO[Clock, Pd2Exception, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceReq  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- performTraxsourceRequest(serviceReq).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      tracks      <- TraxsourceServiceTrack.fromServiceResponse(serviceResp, feed).toZio
    } yield tracks
  }

  private def downloadTrack(trackUri : Uri) : ZIO[Clock, Pd2Exception, Array[Byte]] =
  {
    for {
      request <- ZIO.succeed(buildTraxsourceTrackRequest(trackUri))
      bytes   <- performTraxsourceRequest(request)
    } yield bytes
  }

  private[providers] def buildTraxsourcePageRequest(
    urlTemplate : String,
    dateFrom    : LocalDate,
    dateTo      : LocalDate,
    page        : Int = 1)
  : Either[Pd2Exception, SttpRequest] =
  {
    val domain = "https://traxsource.com"

    val pageParam = if (page != 1) s"&page=$page" else ""

    val uriStr =
      domain ++
        urlTemplate
          .replace("{0}", dateFrom.toString)
          .replace("{1}", dateTo.toString) ++
        pageParam

    val eitherRequest = for {
      uri <- Uri.parse(uriStr)
    } yield
      traxsourceBasicRequest
        .get(uri)
        .response(asByteArray)

    eitherRequest
      .left.map(msg => InternalConfigurationError(msg))
  }

  private def buildTraxsourceServiceRequest(trackIds : List[Int]) : Either[Pd2Exception, SttpRequest] =
  {
    val uriStr = s"https://w-static.traxsource.com/scripts/playlist.php?tracks=${trackIds.mkString(",")}"

    val eitherRequest = for {
      uri <- Uri.parse(uriStr)
    } yield
      traxsourceBasicRequest
        .get(uri)
        .response(asByteArray)

    eitherRequest.left.map(msg => InternalConfigurationError(msg))
  }

  private def buildTraxsourceTrackRequest(uri : Uri) : SttpRequest =
  {
    traxsourceBasicRequest
      .get(uri)
      .response(asByteArray)
  }

  private def performTraxsourceRequest(request : SttpRequest) : ZIO[Clock, Pd2Exception, Array[Byte]] =
  {
    for {
      response            <- connectionsSemaphore.withPermit(
                                  sttpClient.send(request).timeoutFail(ServiceUnavailable("Timeout"))(60.second))
                                .mapError(e => ServiceUnavailable(request.uri.toString() ++ "\n" ++ e.getMessage, Some(e)))
                                .retry(
                                  Schedule.recurWhile[ServiceUnavailable](_ => true) &&
                                  Schedule.recurs(10) &&
                                  Schedule.spaced(5.seconds))
      contentLengthOption =  response.headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
      body                <- response.body.toZio.mapError(s => ServiceUnavailable(s, None))
      _                   <- ZIO.fail(TraxsourceBadContentLength("Bad content length"))
                              .unless(contentLengthOption.forall(_ == body.length))
    } yield body
  }
}

object TraxsourceLive {
  def makeLayer(maxConcurrentConnections : Int): ZLayer[ConsoleProgress with SttpClient, Nothing, Traxsource] =
    ZLayer.fromServicesM[ConsoleProgress.Service, SttpClient.Service, Any, Nothing, Traxsource.Service] {
    case (consoleProgress, sttpClient) => for {
      connectionsSemaphore <- Semaphore.make(maxConcurrentConnections)
    } yield TraxsourceLive(consoleProgress, sttpClient, connectionsSemaphore)
  }
}