package pd2.providers.traxsource

import io.circe
import io.circe.Decoder.Result
import io.circe.{CursorOp, Decoder, HCursor}
import pd2.providers.Pd2Exception.UnexpectedServiceResponse
import pd2.providers.traxsource.TraxsourceServiceTrack.{TraxsourceServiceArtist, TraxsourceServiceLabel}
import pd2.providers.{Pd2Exception, TrackDto}
import sttp.model.Uri

import java.time.{Duration, LocalDate}
import scala.util.{Failure, Success, Try}

final case class TraxsourceServiceTrack(
  feed        : String,
  trackId     : Int,
  artists     : List[TraxsourceServiceArtist],
  title       : String,
  titleUrl    : String,
  trackUrl    : String,
  label       : TraxsourceServiceLabel,
  genre       : String,
  catNumber   : String,
  duration    : Duration,
  releaseDate : LocalDate,
  imageUrl    : Uri,
  mp3Url      : Uri,
  keySig      : String
) {
  val artist = artists.mkString(", ")
  val releaseName = titleUrl.split('/').last.replaceAll("-", " ")
  def toTrackDto : TrackDto =
    TrackDto(artists.map(_.name).mkString(", "), title, label.name, releaseName, releaseDate, duration, feed, trackId)
}

object TraxsourceServiceTrack {

  final case class TraxsourceServiceArtist(id: Int, tag: Int, name: String, webName : String)
  final case class TraxsourceServiceLabel(id : Int, name : String, webName : String)

  private[providers] def fromServiceResponse(response: String, feed : String) : Either[Pd2Exception, List[TraxsourceServiceTrack]] =
  {
    val tryJson = for {
      xml <- Try { scala.xml.XML.loadString(response) }
      innerJson <- Try { (xml \ "data").head.text }
    } yield innerJson

    tryJson match {
      case Failure(exception) => Left(UnexpectedServiceResponse("Malformed traxsource service response", response, Some(exception)))
      case Success(jsonString) =>
        // В ответе сервиса на Traxsource возвращается не просто json внутри xml, но сам json еще и не соответствует
        // спецификации. У него отстутсвуют кавычки у имен полей.
        val fixedJson = jsonString
          .replaceAll("(\\{|\\n\\s+|,\\s)(\\w+):\\s", "$1\"$2\": ")
          .replaceAll("\\\\'", "'")

        // Создаем декодер для каждой страницы, потому что иначе не получается проставить поле feed
        implicit val trackDecoder: Decoder[TraxsourceServiceTrack] = traxsourceServiceTrackDecoder(feed)
        for {
          json <- circe.parser.parse(fixedJson)
            .left.map(pf => UnexpectedServiceResponse(pf.message, fixedJson, None))
          tracks <- json.as[List[TraxsourceServiceTrack]]
            .left.map(dr => UnexpectedServiceResponse(dr.message ++ CursorOp.opsToPath(dr.history), fixedJson, None))
        } yield tracks
    }
  }

  implicit val traxsourceServiceArtistDecoder: Decoder[TraxsourceServiceArtist] = new Decoder[TraxsourceServiceArtist]{
    override def apply(c: HCursor): Decoder.Result[TraxsourceServiceArtist] =
      for {
        id <- c.downN(0).as[Int]
        tag <- c.downN(1).as[Int]
        name <- c.downN(2).as[String]
        webName <- c.downN(3).as[String]
      } yield TraxsourceServiceArtist(id, tag, name, webName)
  }

  implicit val traxsourceServiceLabelDecoder: Decoder[TraxsourceServiceLabel] = new Decoder[TraxsourceServiceLabel] {
    override def apply(c: HCursor) : Decoder.Result[TraxsourceServiceLabel] =
      for {
        id <- c.downN(0).as[Int]
        name <- c.downN(1).as[String]
        webName <- c.downN(2).as[String]
      } yield TraxsourceServiceLabel(id, name, webName)
  }

  implicit val uriDecoder : Decoder[Uri] = new Decoder[Uri] {
    override def apply(c: HCursor): Result[Uri] = for {
      t <- c.value.as[String]
    } yield Uri.parse(t).getOrElse(???)
  }

  def traxsourceServiceTrackDecoder(feed : String): Decoder[TraxsourceServiceTrack] = new Decoder[TraxsourceServiceTrack] {
    override def apply(c: HCursor) : Decoder.Result[TraxsourceServiceTrack] =
      for {
        id              <- c.downField("track_id").as[Int]
        artists         <- c.downField("artist").as[List[TraxsourceServiceArtist]]
        title           <- c.downField("title").as[String]
        titleUrl        <- c.downField("title_url").as[String]
        trackUrl        <- c.downField("track_url").as[String]
        label           <- c.downField("label").as[TraxsourceServiceLabel]
        genre           <- c.downField("genre").as[String]
        catNumber       <- c.downField("catnumber").as[String]
        durationSeconds <- c.downField("duration").as[String] // XX:XX:XX
          .map { s =>
            val multipliers = List(60 * 60, 60, 1)
            val parts = s.split(':').map(_.toInt).toList

            parts.zip(multipliers.drop(multipliers.length - parts.length))
              .map { case (p, m) => p * m }
              .sum
          }
        releaseDate     <- c.downField("r_date").as[LocalDate]
        imageUrl        <- c.downField("image").as[Uri]
        mp3Url          <- c.downField("mp3").as[Uri]
        key             <- c.downField("keysig").as[String]
      } yield TraxsourceServiceTrack(feed,
        id, artists, title, titleUrl, trackUrl, label, genre,
        catNumber, Duration.ofSeconds(durationSeconds), releaseDate, imageUrl, mp3Url, key)
  }

}
