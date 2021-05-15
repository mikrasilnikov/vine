package pd2.web

import pd2.config.TraxsourceFeed
import pd2.helpers.Conversions._
import pd2.ui.ConsoleUIService.ConsoleUI
import pd2.ui.ProgressOps._
import sttp.client3
import sttp.client3.httpclient.zio.{SttpClient, send}
import sttp.client3.{RequestT, asString, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.{Promise, ZIO}
import java.time.LocalDate

object TraxsourceDataProvider {

  type SttpStringRequest = RequestT[client3.Identity, Either[String, String], Any]

  private val traxsourceBasicRequest = basicRequest
    .header(HeaderNames.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  implicit val provider: WebDataProvider[TraxsourceFeed] = new WebDataProvider[TraxsourceFeed] {
    override def processTracks(
      feed        : TraxsourceFeed,
      dateFrom    : LocalDate,
      dateTo      : LocalDate,
      processTrack: TrackDto => ZIO[Any, Pd2Exception, Unit])
    : ZIO[SttpClient with ConsoleUI, Pd2Exception, Unit] =
    {
      for {
        firstPagePromise    <- Promise.make[Nothing, TraxsourceWebPage]
        firstPageFiber      <- processTracklistPage(feed, dateFrom, dateTo, processTrack, 1, Some(firstPagePromise))
                                .withProgressReporting(feed.name)
                                .fork
        firstPage           <- firstPagePromise.await
        _                   <- ZIO.foreachPar_(firstPage.remainingPages) {
                                i => processTracklistPage(feed, dateFrom, dateTo, processTrack, i, None)
                                  .withProgressReporting(feed.name)
                                }
        _                   <- firstPageFiber.join
      } yield ()
    }
  }

  private def processTracklistPage(
    feed             : TraxsourceFeed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    processTrack     : TrackDto => ZIO[Any, Pd2Exception, Unit],
    pageNum          : Int,
    pagePromiseOption: Option[Promise[Nothing, TraxsourceWebPage]]
    )
  : ZIO[SttpClient with ConsoleUI, Pd2Exception, Unit] = {
    for {
      page    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum).withProgressReporting(feed.name)
      _       <- pagePromiseOption.fold(ZIO.succeed(()))(p => p.succeed(page).unit)
      tracks  <- getServiceData(page.trackIds).withProgressReporting(feed.name)
      _       <- ZIO.foreachPar_(tracks) {
                    track => processTrack(track.toTrackDto).withProgressReporting(feed.name)
                 }
    } yield ()
  }

  private def getTracklistWebPage(feed : TraxsourceFeed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
    : ZIO[SttpClient, Pd2Exception, TraxsourceWebPage] =
  {
    for {
      pageReq <- buildTraxsourcePageRequest(feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp <- makeTraxsourcePageRequest(pageReq)
      page <- TraxsourceWebPage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int])
    : ZIO[SttpClient, Pd2Exception, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceReq <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- makeTraxsourceServiceRequest(serviceReq)
      tracks <- TraxsourceServiceTrack.fromServiceResponse(serviceResp).toZio
    } yield tracks
  }

  private[web] def buildTraxsourcePageRequest(
    urlTemplate: String,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    page: Int = 1): Either[Pd2Exception, SttpStringRequest] = {
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

    eitherRequest.left.map(msg => InternalConfigurationError(msg))
  }

  private def makeTraxsourcePageRequest(request: SttpStringRequest) : ZIO[SttpClient, Pd2Exception, String] = {
    for {
      response <- send(request)
        .flatMap(response => ZIO.fromEither(response.body))
    } yield ???

    ???
  }

  private def buildTraxsourceServiceRequest(trackIds : List[Int]) : Either[Pd2Exception, SttpStringRequest] = ???

  private def makeTraxsourceServiceRequest(request : SttpStringRequest) : ZIO[SttpClient, Pd2Exception, String] = ???
}

