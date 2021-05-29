package pd2.providers.beatport

import io.circe
import io.circe.generic.auto._
import io.circe.{CursorOp, Decoder, HCursor}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.{element, elements}
import pd2.providers.{Pager, TrackDto}
import pd2.providers.Pd2Exception.UnexpectedServiceResponse
import sttp.model.Uri

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
  releaseDate : LocalDate,
  label       : String,
  duration    : Option[Duration],
  previewUrl  : Uri,
  key         : Option[String]
) {
  def toTrackDto(feed : String) : TrackDto = TrackDto(
    artists.map(_.name).mkString(", "),
    if (mix.isBlank) name else s"$name ($mix)",
    label,
    releaseDate,
    feed,
    id)
}




case class BeatportPage(pager : Option[Pager], tracks : List[BeatportPageTrack]) {
  val remainingPages: List[Int] = pager match {
    case None => Nil
    case Some(Pager(current, total)) => (current + 1 to total).toList
  }
}

object BeatportPage {
  private val browser = new JsoupBrowser()

  def parse(html : String) : Either[UnexpectedServiceResponse, BeatportPage] = {

    val tryDoc = Try { browser.parseString(html) }
    tryDoc match {
      case Failure(exception) => Left(UnexpectedServiceResponse(exception.getMessage, html, Some(exception)))
      case Success(doc) =>
        for {
          visibleTrackIds <- parseVisibleTrackIds(doc, html)
          scriptTracks    <- parseTracksJson(doc, html)
          pager           <- parsePager(doc)
          // Битпорт иногда выдает в скрипте на странице больше треков, чем видно пользователю.
          visibleTracks = visibleTrackIds.map(id => scriptTracks.filter(_.id == id).head)
        } yield BeatportPage(pager, visibleTracks)
    }
  }

  private def parseVisibleTrackIds(doc : JsoupDocument, html : String) : Either[UnexpectedServiceResponse, List[Int]] = {
    doc >?> elements("li.track") match {
      case None => Left(UnexpectedServiceResponse("Could not find tracks on page", html, None))
      case Some(els) => Right(els.map(el => el.attr("data-ec-id").toInt).toList)
    }
  }

  private def parseTracksJson(doc : JsoupDocument, html : String) : Either[UnexpectedServiceResponse, List[BeatportPageTrack]] = {
    doc >?> element("script#data-objects") match {
      case None => Left(UnexpectedServiceResponse("Could not find script#data-objects", html, None))
      case Some(el) => {
        val script = el.innerHtml
        val playablesPrefix = "window.Playables = "
        val playablesIndex = script.indexOf(playablesPrefix)
        if (playablesIndex == -1)
          Left(UnexpectedServiceResponse(s"Could not find $playablesPrefix", html, None))
        else {
          val jsonStr = script.substring(playablesIndex + playablesPrefix.length, script.indexOf("};") + 1)
          for {
            json <- circe.parser.parse(jsonStr)
              .left.map(pf => UnexpectedServiceResponse(pf.message, jsonStr, None))
            tracks <- json.hcursor.downField("tracks").as[List[BeatportPageTrack]]
              .left.map(dr => UnexpectedServiceResponse(dr.message ++ CursorOp.opsToPath(dr.history), jsonStr, None))
          } yield tracks
        }
      }
    }
  }

  private def parsePager(doc : JsoupDocument) : Either[UnexpectedServiceResponse, Option[Pager]] = {
    doc >?> element("div.pag-numbers") match {
      case None => Right(None)
      case Some(el) =>
        val currentPg = (el >> element("strong.pag-number-current")).text.toInt
        val lastPg = (el >> elements(".pag-number")).last.text.toInt
        Right(Some(Pager(currentPg, lastPg)))
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
        releaseDate <- c.downField("date").downField("released").as[LocalDate]
        label       <- c.downField("label").downField("name").as[String]
        duration    <- c.downField("duration").downField("milliseconds").as[Option[Int]]
                        .map(mso => mso.map(ms => Duration.ofMillis(ms)))
        previewUrl  <- c.downField("preview").downField("mp3").downField("url").as[Uri]
        key         <- c.downField("key").as[Option[String]]
      } yield BeatportPageTrack(id, artists, title, name, mix, release, releaseDate, label, duration, previewUrl, key)
  }
}