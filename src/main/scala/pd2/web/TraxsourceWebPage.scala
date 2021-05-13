package pd2.web

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Document
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, attrs, element, elements}
import pd2.web.TraxsourceWebPage.TraxsourcePager
import scala.util.{Failure, Success, Try}


final case class TraxsourceWebPage(pager : TraxsourcePager, trackIds : List[Int])

object TraxsourceWebPage {

  private val browser = new JsoupBrowser()

  sealed trait TraxsourcePager
  case object Absent extends TraxsourcePager
  final case class Present(currentPage : Int, lastPage : Int) extends TraxsourcePager

  private[web] def parse(html : String) : Either[ParseFailure, TraxsourceWebPage] =
  {
    val tryDoc = Try { browser.parseString(html) }

    tryDoc match {
      case Failure(exception) => Left(ParseFailure(exception.getMessage, html, Some(exception)))
      case Success(doc) => readDocument(html, doc)
    }
  }

  private[web] def readDocument(html : String, doc : Document) : Either[ParseFailure, TraxsourceWebPage] =
  {
    val trackListDiv = doc >?> element("div.trk-list-cont")

    val pagerEither = trackListDiv match {
      case None => Left(ParseFailure("Could not find element div.trk-list-cont", html, None))
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
      TraxsourceWebPage(pager, trackIds)
    }
  }
}



