package pd2.providers.beatport

import io.circe
import io.circe.{CursorOp, Decoder, HCursor}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import sttp.model.Uri
import io.circe.generic.auto._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import pd2.providers.Pd2Exception.UnexpectedServiceResponse
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Document
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{attr, attrs, element, elements}

import java.time.temporal.TemporalAmount
import java.time.{Duration, LocalDate}
import scala.util.{Failure, Success, Try}

case class BeatportPageArtist(id : Int, name : String, slug : String)

case class BeatportPageTrack(
  id          : Int,
  artists     : List[BeatportPageArtist],
  title       : String,
  name        : String,
  mix         : String,
  release     : String,
  label       : String,
  duration    : Duration,
  previewUrl  : Uri,
  key         : String
)

case class BeatportPager(currentPage : Int, totalPages : Int)
case class BeatportPage(pager : Option[BeatportPager], tracks : List[BeatportPageTrack])

object BeatportPage {
  private val browser = new JsoupBrowser()

  def parse(html : String) : Either[UnexpectedServiceResponse, BeatportPage] = {

    val tryDoc = Try { browser.parseString(html) }

    tryDoc match {
      case Failure(exception) => Left(UnexpectedServiceResponse(exception.getMessage, html, Some(exception)))
      case Success(doc) =>
        doc >?> element("script.data-objects") match {
          case None => Left(UnexpectedServiceResponse("Could not find script.data-objects", html, None))
          case Some(el) => {
            val script = el.text
            val playablesIndex = script.indexOf("window.playables=")
              if (playablesIndex == -1)
                Left(UnexpectedServiceResponse("Could not find window.playables=", html, None))
              else {
                val jsonStr = script.substring(playablesIndex + "window.playables=".length, script.indexOf("};") + 1)
                for {
                  tracks <- parseTracks(jsonStr)
                  pager <- parsePager(doc)
                } yield BeatportPage(pager, tracks)
              }
          }
        }
    }
  }

  def parseTracks(jsonStr: String) : Either[UnexpectedServiceResponse, List[BeatportPageTrack]] = {
    for {
      json <- circe.parser.parse(jsonStr)
        .left.map(pf => UnexpectedServiceResponse(pf.message, jsonStr, None))
      tracks <- json.hcursor.downField("tracks").as[List[BeatportPageTrack]]
        .left.map(dr => UnexpectedServiceResponse(dr.message ++ CursorOp.opsToPath(dr.history), jsonStr, None))
    } yield tracks
  }

  def parsePager(doc : JsoupDocument) : Either[UnexpectedServiceResponse, Option[BeatportPager]] = {
    doc >?> element("div.pag-numbers") match {
      case None => Right(None)
      case Some(el) =>
        val currentPg = (el >> element("strong.pag-number-current")).text.toInt
        val lastPg = (el >> elements("a.pag-number")).last.text.toInt
        Right(Some(BeatportPager(currentPg, lastPg)))
    }
  }

  import pd2.providers.traxsource.TraxsourceServiceTrack.uriDecoder
  implicit val beatportTrackDecoder : Decoder[BeatportPageTrack] = new Decoder[BeatportPageTrack] {
    override def apply(c: HCursor) : Decoder.Result[BeatportPageTrack] =
      for {
        id          <- c.downField("id").as[Int]
        artists     <- c.downField("artists").as[List[BeatportPageArtist]]
        title       <- c.downField("title").as[String]
        name        <- c.downField("name").as[String]
        mix         <- c.downField("mix").as[String]
        release     <- c.downField("release").downField("name").as[String]
        label       <- c.downField("label").downField("name").as[String]
        duration    <- c.downField("duration").downField("milliseconds").as[Int].map(ms => Duration.ofMillis(ms))
        previewUrl  <- c.downField("preview").downField("mp3").downField("url").as[Uri]
        key         <- c.downField("key").as[String]
      } yield BeatportPageTrack(id, artists, title, name, mix, release, label, duration, previewUrl, key)
  }
}