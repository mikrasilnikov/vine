package pd2.config

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}
import pd2.config.ConfigModel._
import io.circe.generic.auto._

object ConfigDecoders {
  implicit val filterTagDecoder : Decoder[FilterTag] = new Decoder[FilterTag] {
    override def apply(c: HCursor): Result[FilterTag] =
      for {
        value <- c.value.as[String]
      } yield value match {
        case "my"       => FilterTag.My
        case "noShit"   => FilterTag.IgnoredLabels
        case "noEdits"  => FilterTag.NoEdits
        case _          => FilterTag.Empty
      }
  }

  implicit val feedDecoder: Decoder[Feed] = new Decoder[Feed] {
    final def apply(c: HCursor): Result[Feed] =
      for {
        name        <- c.downField("name").as[String]
        provider    <- c.downField("provider").as[String]
        urlTemplate <- c.downField("urlTemplate").as[String]
        filters     <- c.downField("filters").as[List[FilterTag]]
        priority    <- c.downField("priority").as[Option[Int]]
      } yield {
        provider match {
          case "beatport" => Feed(FeedTag.BeatportFeed, name, urlTemplate, filters, priority)
          case "traxsource" => Feed(FeedTag.TraxsourceFeed, name, urlTemplate, filters, priority)
        }
      }
  }

  implicit val sourcesDecoder: Decoder[SourcesConfig] = new Decoder[SourcesConfig] {
    final def apply(c: HCursor): Decoder.Result[SourcesConfig] =
      for {
        previewsFolder <- c.downField("previewsFolder").as[String]
        my             <- c.downField("filters").downField("my").as[FilterConfig.My]
        noShit         <- c.downField("filters").downField("noShit").as[FilterConfig.NoShit]
        noEdits        <- c.downField("filters").downField("noEdits").as[FilterConfig.NoEdits]
        feeds          <- c.downField("feeds").as[List[Feed]]
      } yield {
        SourcesConfig(previewsFolder, FiltersConfig(my, noShit, noEdits), feeds)
      }
  }

  implicit val genreDecoder: Decoder[Genre] = new Decoder[Genre] {
    final def apply(c: HCursor): Decoder.Result[Genre] =
      for {
        name  <- c.downField("name").as[String]
        feeds <- c.downField("feeds").as[List[Feed]]
      } yield Genre(name, feeds)
  }

  implicit val genresDecoder: Decoder[GenresConfig] = new Decoder[GenresConfig] {
    final def apply(c: HCursor): Decoder.Result[GenresConfig] =
      for {
        previewsFolder <- c.downField("previewsFolder").as[String]
        my             <- c.downField("filters").downField("my").as[FilterConfig.My]
        noShit         <- c.downField("filters").downField("noShit").as[FilterConfig.NoShit]
        noEdits        <- c.downField("filters").downField("noEdits").as[FilterConfig.NoEdits]
        genres         <- c.downField("genres").as[List[Genre]]
      } yield {
        GenresConfig(previewsFolder, FiltersConfig(my, noShit, noEdits), genres)
      }
  }
}
