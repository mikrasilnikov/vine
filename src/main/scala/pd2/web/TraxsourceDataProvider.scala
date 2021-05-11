package pd2.web

import net.ruippeixotog.scalascraper.model.Document
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, attrs, element, elements}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import pd2.config.TraxsourceFeed
import sttp.client3
import sttp.client3.httpclient.zio.send
import sttp.client3.{RequestT, asString, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio.{Schedule, Task, ZIO}

import java.time.LocalDate

object TraxsourceDataProvider {

  type SttpRequest = RequestT[client3.Identity, Either[String, String], Any]

  sealed trait TraxsourcePager
  case object Absent extends TraxsourcePager
  case class Present(currentPage : Int, lastPage : Int) extends TraxsourcePager

  case class TraxsourcePage(pager : TraxsourcePager, trackIds : List[Int])

  private val traxsourceBasicRequest = basicRequest
    .header(HeaderNames.UserAgent,"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")

  implicit val provider: WebDataProvider[TraxsourceFeed] = new WebDataProvider[TraxsourceFeed] {
    override def processTracks(feed: TraxsourceFeed, dateFrom : LocalDate, dateTo : LocalDate, processAction: TrackDto => Task[Unit]) = {

      ???

    }
  }

  private[web] def buildTraxsourcePageRequest(
    urlTemplate : String,
    dateFrom : LocalDate,
    dateTo : LocalDate,
    page : Int = 1): Either[String, SttpRequest] =
  {
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

  private def makeTraxsourcePageRequest(request : SttpRequest) = {
    for {
      response <- send(request)
        .flatMap(response => ZIO.fromEither(response.body))
    } yield ???
  }

  private[web] def parseTraxsourcePage(doc : Document) : Either[String, TraxsourcePage] =
  {
    val trackListDiv = doc >?> element("div.trk-list-cont")

    val pagerEither : Either[String, TraxsourcePager] = trackListDiv match {
      case None => Left("Could not find element div.trk-list-cont")
      case Some(div) =>
        val pageAnchors = div >?> element("div.list-pager") >?> element("div.page-nums")
        pageAnchors.flatten match {
          case None => Right(Absent)
          case Some(elementQuery) =>
            val currentPageNum = elementQuery >> element("a.active-page") >> attr("data-pg")
            val lastPageNum = (elementQuery >> elements("a")).last.attr("data-pg")
            Right(Present(currentPageNum.toInt, lastPageNum.toInt))
        }
    }

    pagerEither.map { pager =>
      val tracklistDiv = doc >> elements("div.play-trk") >> attrs("data-trid")
      val trackIds = tracklistDiv.map(_.toInt).toList
      TraxsourcePage(pager, trackIds)
    }
  }
}
