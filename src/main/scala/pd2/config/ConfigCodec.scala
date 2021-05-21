package pd2.config

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import pd2.config.ConfigDescription._

sealed trait FilterConfig
sealed trait FilterTag
sealed trait Feed

case class ConfigDescription(
   previewsFolder : String,
   my             : FilterConfig.My,
   onlyNew        : FilterConfig.OnlyNew,
   noShit         : FilterConfig.NoShit,
   noCompilations : FilterConfig.NoCompilations,
   noEdits        : FilterConfig.NoEdits,
   feeds          : List[Feed])

object ConfigDescription {

  object FilterConfig {
    case class My(artistsFile: String, labelsFile: String) extends FilterConfig
    case class OnlyNew(dataPath: String, fileTemplate: String) extends FilterConfig
    case class NoShit(dataFiles: List[String]) extends FilterConfig
    case class NoCompilations(maxTracksPerRelease: Int) extends FilterConfig
    case class NoEdits(minTrackDurationSeconds: Int) extends FilterConfig
  }

  object FilterTag {
    case object Empty           extends FilterTag
    case object My              extends FilterTag
    case object OnlyNew         extends FilterTag
    case object NoShit          extends FilterTag
    case object NoCompilations  extends FilterTag
    case object NoEdits         extends FilterTag
  }

  object Feed {
    case class BeatportFeed(name : String, urlTemplate : String, filters : List[FilterTag]) extends Feed
    case class TraxsourceFeed(name : String, urlTemplate : String, filters : List[FilterTag]) extends Feed
  }

  implicit val filterTagDecoder : Decoder[FilterTag] = new Decoder[FilterTag] {
    override def apply(c: HCursor): Result[FilterTag] =
      for {
        value <- c.value.as[String]
      } yield value match {
        case "my"               => FilterTag.My
        case "onlyNew"          => FilterTag.OnlyNew
        case "noShit"           => FilterTag.NoShit
        case "noCompilations"   => FilterTag.NoCompilations
        case "noEdits"          => FilterTag.NoEdits
      }
  }

  implicit val feedDecoder: Decoder[Feed] = new Decoder[Feed] {
    final def apply(c: HCursor): Result[Feed] =
      for {
        name        <- c.downField("name").as[String]
        provider    <- c.downField("provider").as[String]
        urlTemplate <- c.downField("urlTemplate").as[String]
        filters     <- c.downField("filters").as[List[FilterTag]]
      } yield {
        provider match {
          case "beatport" => Feed.BeatportFeed(name, urlTemplate, filters)
          case "traxsource" => Feed.TraxsourceFeed(name, urlTemplate, filters)
        }
      }
  }

  implicit val configDecoder: Decoder[ConfigDescription] = new Decoder[ConfigDescription] {
    final def apply(c: HCursor): Decoder.Result[ConfigDescription] =
      for {
        previewsFolder <- c.downField("previewsFolder").as[String]
        my             <- c.downField("filters").downField("my").as[FilterConfig.My]
        onlyNew        <- c.downField("filters").downField("onlyNew").as[FilterConfig.OnlyNew]
        noShit         <- c.downField("filters").downField("noShit").as[FilterConfig.NoShit]
        noCompilations <- c.downField("filters").downField("noCompilations").as[FilterConfig.NoCompilations]
        noEdits        <- c.downField("filters").downField("noEdits").as[FilterConfig.NoEdits]
        feeds          <- c.downField("feeds").as[List[Feed]]
      } yield {
        ConfigDescription(previewsFolder, my, onlyNew, noShit, noCompilations, noEdits, feeds)
      }
  }
}
