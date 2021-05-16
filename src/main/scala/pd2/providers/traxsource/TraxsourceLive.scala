package pd2.providers.traxsource

import pd2.config.TraxsourceFeed
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Pd2Exception.{InternalConfigurationError, ServiceUnavailable}
import pd2.providers.{Pd2Exception, TrackDto, TraxsourceServiceTrack, TraxsourceWebPage}
import pd2.ui.ProgressBar.InProgress
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import sttp.client3
import sttp.client3.httpclient.zio.SttpClient
import sttp.client3.{RequestT, asString, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.{Has, Promise, Semaphore, ZIO, ZLayer}
import java.time.LocalDate
import scala.util.Random

case class TraxsourceLive(
  consoleProgress       : ConsoleProgress.Service,
  sttpClient            : SttpClient.Service,
  connectionsSemaphore  : Semaphore)
  extends Traxsource.Service
{
  type SttpStringRequest = RequestT[client3.Identity, Either[String, String], Any]

  private val traxsourceBasicRequest = basicRequest
    .header(HeaderNames.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  override def processTracks[R](
    feed          : TraxsourceFeed,
    dateFrom      : LocalDate,
    dateTo        : LocalDate,
    processTrack  : TrackDto => ZIO[R, Pd2Exception, Unit])
  : ZIO[R, Pd2Exception, Unit] = for {
    firstPageProgress <- consoleProgress.acquireProgressItem(feed.name)
    firstPagePromise  <- Promise.make[Nothing, TraxsourceWebPage]
    firstPageFiber    <- processTracklistPage(feed, dateFrom, dateTo, processTrack, 1, firstPageProgress, Some(firstPagePromise)).fork
    firstPage         <- firstPagePromise.await
    remainingProgress <- consoleProgress.acquireProgressItems(feed.name, firstPage.remainingPages.length)
    shuffled          =  shuffle(firstPage.remainingPages zip remainingProgress)
    _                 <- ZIO.foreachPar_(shuffled) { case (i, p) =>
                            consoleProgress.updateProgressItem(p, InProgress) *>
                            processTracklistPage(feed, dateFrom, dateTo, processTrack, i, p, None) *>
                            consoleProgress.completeProgressItem(p)
                        }
    _                 <- firstPageFiber.join
  } yield ()

  private def processTracklistPage[R](
    feed             : TraxsourceFeed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    processTrack     : TrackDto => ZIO[R, Pd2Exception, Unit],
    pageNum          : Int,
    pageProgressItem : ProgressItem,
    pagePromiseOption: Option[Promise[Nothing, TraxsourceWebPage]])
  : ZIO[R, Pd2Exception, Unit] =
  {
    for {
      page      <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum)
      _         <- pagePromiseOption match {
                      case Some(promise) => promise.succeed(page).unit
                      case None => ZIO.succeed() }
      tracks    <- getServiceData(page.trackIds)
      _         <- consoleProgress.completeProgressItem(pageProgressItem)
      progress  <- consoleProgress.acquireProgressItems(feed.name, tracks.length)
      shuffled  =  shuffle(tracks zip progress)
      _         <- ZIO.foreachPar_(shuffled) { case (t, p) =>
                      connectionsSemaphore.withPermit(
                        consoleProgress.updateProgressItem(p, InProgress) *>
                        processTrack(t.toTrackDto(Array[Byte]())) *>
                        consoleProgress.completeProgressItem(p))
                   }
    } yield ()
  }

  private def getTracklistWebPage(feed : TraxsourceFeed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
  : ZIO[Any, Pd2Exception, TraxsourceWebPage] =
  {
    for {
      pageReq   <- buildTraxsourcePageRequest(feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- makeTraxsourcePageRequest(pageReq)
      page      <- TraxsourceWebPage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int])
  : ZIO[Any, Pd2Exception, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceReq  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- makeTraxsourceServiceRequest(serviceReq)
      tracks      <- TraxsourceServiceTrack.fromServiceResponse(serviceResp).toZio
    } yield tracks
  }

  private[providers] def buildTraxsourcePageRequest(
    urlTemplate : String,
    dateFrom    : LocalDate,
    dateTo      : LocalDate,
    page        : Int = 1)
  : Either[Pd2Exception, SttpStringRequest] =
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
        .response(asString)

    eitherRequest
      .left.map(msg => InternalConfigurationError(msg))
      //.map { req => println(req.uri); req }
  }

  private def makeTraxsourcePageRequest(request: SttpStringRequest) : ZIO[Any, Pd2Exception, String] =
  {
    val effect = for {
      response  <- sttpClient.send(request).mapError(e => ServiceUnavailable(e.getMessage, Some(e)))
      body      <- response.body.toZio.mapError(s => ServiceUnavailable(s, None))
    } yield body

    connectionsSemaphore.withPermit(effect)
  }

  private def buildTraxsourceServiceRequest(trackIds : List[Int]) : Either[Pd2Exception, SttpStringRequest] =
  {
    val uriStr = s"https://w-static.traxsource.com/scripts/playlist.php?tracks=${trackIds.mkString(",")}"

    val eitherRequest = for {
      uri <- Uri.parse(uriStr)
    } yield
      traxsourceBasicRequest
        .get(uri)
        .response(asString)

    eitherRequest.left.map(msg => InternalConfigurationError(msg))
  }

  private def makeTraxsourceServiceRequest(request : SttpStringRequest) : ZIO[Any, Pd2Exception, String] =
  {
    val effect = for {
      response  <- sttpClient.send(request).mapError(e => ServiceUnavailable(e.getMessage, Some(e)))
      body      <- response.body.toZio.mapError(s => ServiceUnavailable(s, None))
    } yield body

    connectionsSemaphore.withPermit(effect)
  }

  private def shuffle[A](as : List[A]) : List[A] =
    (as zip as.indices.map(_ => Random.nextInt()))
      .sortBy { case (_, rnd) => rnd }
      .map    { case (a, _) => a }
}

object TraxsourceLive {
  def makeLayer(maxConcurrentConnections : Int): ZLayer[Has[ConsoleProgress.Service] with Has[SttpClient.Service], Nothing, Has[Traxsource.Service]] =
    ZLayer.fromServicesM[ConsoleProgress.Service, SttpClient.Service, Any, Nothing, Traxsource.Service] {
    case (consoleProgress, sttpClient) => for {
      semaphore <- Semaphore.make(maxConcurrentConnections)
    } yield TraxsourceLive(consoleProgress, sttpClient, semaphore)
  }
}