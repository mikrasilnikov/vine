package pd2.web


import pd2.config.TraxsourceFeed
import sttp.client3
import sttp.client3.httpclient.zio.send
import sttp.client3.{RequestT, asString, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.{Schedule, Task, ZIO}

import java.time.LocalDate

object TraxsourceDataProvider {

  type SttpRequest = RequestT[client3.Identity, Either[String, String], Any]

  private val traxsourceBasicRequest = basicRequest
    .header(HeaderNames.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  implicit val provider: WebDataProvider[TraxsourceFeed] = new WebDataProvider[TraxsourceFeed] {
    override def processTracks(feed: TraxsourceFeed, dateFrom: LocalDate, dateTo: LocalDate, processAction: TrackDto => Task[Unit]) = {

      ???

    }
  }

  private[web] def buildTraxsourcePageRequest(
    urlTemplate: String,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    page    : Int = 1): Either[String, SttpRequest] = {
    val domain = "https://traxsource.com"

    val pageParam = if (page != 1) s"&page=$page" else ""

    val uriStr =
      domain ++
        urlTemplate
          .replace("{0}", dateFrom.toString)
          .replace("{1}", dateTo.toString) ++
        pageParam

    for {
      uri <- Uri.parse(uriStr)
    } yield
      traxsourceBasicRequest
        .get(uri)
        .response(asString)
  }

  private def makeTraxsourcePageRequest(request: SttpRequest) = {
    for {
      response <- send(request)
        .flatMap(response => ZIO.fromEither(response.body))
    } yield ???
  }
}

