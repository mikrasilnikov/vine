import TraxsourceDownloadTest.parseTraxsourcePage
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.scraper.ContentExtractors._
import sttp.client3.{RequestT, UriContext, asByteArray, asFile, asString, basicRequest}
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model.Document
import sttp.client3
import sttp.model.{HeaderNames, Uri}
import sttp.client3.httpclient.zio._
import zio._
import zio.console.putStrLn

import java.io.File
import java.time.LocalDate

object TraxsourceDownloadTest extends zio.App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val urlTemplate = "/just-added?cn=tracks&ipp=100&period={0},{1}" // 2021-05-02,2021-05-08
    val date1 = LocalDate.of(2021, 5, 2)
    val date2 = LocalDate.of(2021, 5, 8)

    val firstRequest = buildTraxsourceRequest(urlTemplate, date1, date2)
    
    val effect = for {
      response <- send(firstRequest)
      body <- ZIO.fromEither(response.body)
      browser = JsoupBrowser()
      resHead <- ZIO.fromEither(parseTraxsourcePage(browser.parseString(body)))

      requests = resHead.pager match {
        case Absent => Nil
        case Present(current, total) =>
          (current + 1 to total).map(i => buildTraxsourceRequest(urlTemplate, date1, date2, i))
      }

      resTail <- ZIO.foreachParN(8)(requests) { req =>
        for {
          eitherBody <- send(req).map(_.body)
          body <- ZIO.fromEither(eitherBody)
          result <- ZIO.fromEither(parseTraxsourcePage(browser.parseString(body)))
          _ <- putStrLn(s"${result.trackIds.length}")
        } yield result
      }
      result = resHead :: resTail.toList

      _ <- putStrLn(s"Total: ${result.map(_.trackIds.length).sum}")
    } yield ()

    effect.provideCustomLayer(HttpClientZioBackend.layer()).exitCode

  }

  def buildTraxsourceRequest(urlTemplate : String, dateFrom : LocalDate, dateTo : LocalDate, page : Int = 1): RequestT[client3.Identity, Either[String, String], Any] = {

    val domain = "https://traxsource.com"

    val pageParam = if (page != 1) s"&page=$page" else ""

    val uri =
      domain ++
      urlTemplate
        .replace("{0}", dateFrom.toString)
        .replace("{1}", dateTo.toString) ++
      pageParam

    basicRequest
      .get(Uri.parse(uri).right.get)
      .header(HeaderNames.UserAgent,"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
      .response(asString)
  }

  sealed trait Pager
  case object Absent extends Pager
  case class Present(currentPage : Int, lastPage : Int) extends Pager
  case class TraxsourcePage(pager : Pager, trackIds : List[Int])

  def parseTraxsourcePage(doc : Document) : Either[String, TraxsourcePage] = {
    val pagerDiv = doc >?> element("div.list-pager.top")

    val pagerEither : Either[String, Pager] = pagerDiv match {
      case None => Left("Could not find element div.list-pager.top")
      case Some(el) =>
        val pageAnchors = el >?> element("div.page-nums")
        pageAnchors match {
          case None => Right(Absent)
          case Some(elementQuery) =>
            val currentPage = elementQuery >> element("a.active-page") >> attr("data-pg")
            val lastPage = (elementQuery >> elements("a")).last.attr("data-pg")
            Right(Present(currentPage.toInt, lastPage.toInt))
        }
    }

    pagerEither.map { pager =>
      val tracklistDiv = doc >> elements("div.play-trk") >> attrs("data-trid")
      val trackIds = tracklistDiv.map(_.toInt).toList
      TraxsourcePage(pager, trackIds)
    }
  }

}
