package pd2.web

import io.circe.{CursorOp, Decoder, HCursor}

import java.time.LocalDate
import scala.util.{Failure, Success, Try}
import io.circe
import pd2.web.TraxsourceServiceTrack.{TraxsourceServiceArtist, TraxsourceServiceLabel}

final case class TraxsourceServiceTrack(
  trackId : Int,
  artists : List[TraxsourceServiceArtist],
  title : String,
  titleUrl : String,
  trackUrl : String,
  label : TraxsourceServiceLabel,
  genre : String,
  catNumber : String,
  durationSeconds : Int,
  releaseDate : LocalDate,
  imageUrl : String,
  mp3Url : String,
  keySig : String
)

object TraxsourceServiceTrack {

  final case class TraxsourceServiceArtist(id: Int, tag: Int, name: String, webName : String)
  final case class TraxsourceServiceLabel(id : Int, name : String, webName : String)

  private[web] def fromServiceResponse(response: String) : Either[ParseFailure, List[TraxsourceServiceTrack]] =
  {
    val tryJson = for {
      xml <- Try { scala.xml.XML.loadString(response) }
      innerJson <- Try { (xml \ "data").head.text }
    } yield innerJson

    tryJson match {
      case Failure(exception) => Left(ParseFailure("Malformed traxsource service response", response, Some(exception)))
      case Success(jsonString) =>
        // В ответе сервиса на Traxsource возвращается не просто json внутри xml, но сам json еще и не соответствует
        // спецификации. У него отстутсвуют кавычки у имен полей.
        val fixedJson = jsonString.replaceAll("(\\w+):\\s", "\"$1\": ")
        circe.DecodingFailure
        for {
          json <- circe.parser.parse(fixedJson)
            .left.map(pf => ParseFailure(pf.message, fixedJson, None))
          tracks <- json.as[List[TraxsourceServiceTrack]]
            .left.map(dr => ParseFailure(dr.message ++ CursorOp.opsToPath(dr.history), fixedJson, None))
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

  implicit val traxsourceServiceTrackDecoder: Decoder[TraxsourceServiceTrack] = new Decoder[TraxsourceServiceTrack] {
    override def apply(c: HCursor) : Decoder.Result[TraxsourceServiceTrack] =
      for {
        id <- c.downField("track_id").as[Int]
        artists <- c.downField("artist").as[List[TraxsourceServiceArtist]]
        title <- c.downField("title").as[String]
        titleUrl <- c.downField("title_url").as[String]
        trackUrl <- c.downField("track_url").as[String]
        label <- c.downField("label").as[TraxsourceServiceLabel]
        genre <- c.downField("genre").as[String]
        catNumber <- c.downField("catnumber").as[String]
        duration <- c.downField("duration").as[String] // XX:XX:XX
          .map { s =>
            val multipliers = List(60 * 60, 60, 1)
            val parts = s.split(':').map(_.toInt).toList

            parts.zip(multipliers.drop(multipliers.length - parts.length))
              .map { case (p, m) => p * m }
              .sum
          }
        releaseDate <- c.downField("r_date").as[LocalDate]
        imageUrl <- c.downField("image").as[String]
        mp3Url <- c.downField("mp3").as[String]
        key <- c.downField("keysig").as[String]
      } yield TraxsourceServiceTrack(
        id, artists, title, titleUrl, trackUrl, label, genre,
        catNumber, duration, releaseDate, imageUrl, mp3Url, key)
  }

}
