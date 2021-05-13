package pd2.web


import pd2.config.TraxsourceFeed
import sttp.client3
import sttp.client3.httpclient.zio.{SttpClient, send}
import sttp.client3.{RequestT, asString, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.{Has, Schedule, Task, ZIO}
import pd2.ui.{ConsoleUIService, ProgressBar}
import pd2.ui.ConsoleUIService.ConsoleUI

import java.time.LocalDate

object TraxsourceDataProvider {

  type SttpStringRequest = RequestT[client3.Identity, Either[String, String], Any]

  private val traxsourceBasicRequest = basicRequest
    .header(HeaderNames.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  implicit val provider: WebDataProvider[TraxsourceFeed] = new WebDataProvider[TraxsourceFeed] {
    override def processTracks(feed: TraxsourceFeed, dateFrom: LocalDate, dateTo: LocalDate, processAction: TrackDto => ZIO[Any, Pd2Exception, Unit]) : Task[Unit] = {

      def withProgressReporting[R, E, A](batchName : String)(effect : ZIO[R, E, A]) = {
        import ConsoleUIService._
        for {
          item    <- aquireProgressItem(batchName)
          _       <- updateProgressItem(item, ProgressBar.InProgress)
          result  <- effect.tapError(_ => failProgressItem(item))
          _       <- completeProgressItem(item)
        } yield result
      }

      val effect : ZIO[ConsoleUI with SttpClient, Pd2Exception, Unit] =
        for {
          page <- withProgressReporting(feed.name) {
            for {
              pageReq   <- ZIO.fromEither(buildTraxsourcePageRequest(feed.urlTemplate, dateFrom, dateTo))
              pageResp  <- makeTraxsourcePageRequest(pageReq)
              page      <- ZIO.fromEither(TraxsourceWebPage.parse(pageResp))
            } yield page
          }
          tracks <- withProgressReporting(feed.name) {
            for {
              serviceReq  <- ZIO.fromEither(buildTraxsourceServiceRequest(page.trackIds))
              serviceResp <- makeTraxsourceServiceRequest(serviceReq)
              tracks      <- ZIO.fromEither(TraxsourceServiceTrack.fromServiceResponse(serviceResp))
            } yield tracks
          }
          _  <- ZIO.foreachPar_(tracks) { track => withProgressReporting(feed.name)(processAction(track.toTrackDto)) }
        } yield ()

      ???
    }
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

