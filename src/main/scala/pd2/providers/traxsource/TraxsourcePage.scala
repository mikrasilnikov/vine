package pd2.providers.traxsource

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Document
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, attrs, element, elements}
import pd2.providers.Pager
import pd2.providers.Pd2Exception._

import scala.util.{Failure, Success, Try}


final case class TraxsourcePage(pager: Option[Pager], trackIds: List[Int]) {
  val remainingPages: List[Int] = pager.fold(Nil:List[Int])(_.remainingPages)
}

object TraxsourcePage {

  private val browser = new JsoupBrowser()

  private[providers] def parse(html: String) : Either[UnexpectedServiceResponse, TraxsourcePage] =
  {
    val tryDoc = Try { browser.parseString(html) }

    tryDoc match {
      case Failure(exception) => Left(UnexpectedServiceResponse(exception.getMessage, html, Some(exception)))
      case Success(doc) => readDocument(html, doc)
    }
  }

  private[providers] def readDocument(html: String, doc: Document) : Either[UnexpectedServiceResponse, TraxsourcePage] =
  {
    val trackListDiv = doc >?> element("div.trk-list-cont")

    val pagerEither : Either[UnexpectedServiceResponse, Option[Pager]] = trackListDiv match {
      case None => Left(UnexpectedServiceResponse("Could not find element div.trk-list-cont", html, None))
      case Some(div) =>
        val pageAnchors = div >?> element("div.list-pager") >?> element("div.page-nums")
        pageAnchors.flatten match {
          case None => Right(None)
          case Some(elementQuery) =>
            val currentPageNum = elementQuery >> element("a.active-page") >> attr("data-pg")
            val lastPageNum = (elementQuery >> elements("a")).last.attr("data-pg")
            Right(Some(Pager(currentPageNum.toInt, lastPageNum.toInt)))
        }
    }

    pagerEither.map { pager =>
      val tracklistDiv = doc >> elements("div.play-trk") >> attrs("data-trid")
      val trackIds = tracklistDiv.map(_.toInt).toList
      TraxsourcePage(pager, trackIds)
    }
  }
}




