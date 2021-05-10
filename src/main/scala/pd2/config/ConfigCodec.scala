package pd2.config

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._

case class My(artistsFile: String, labelsFile: String)
case class OnlyNew(dataPath: String, fileTemplate: String)
case class NoShit(dataFiles : List[String])
case class NoCompilations(maxTracksPerRelease: Int)
case class NoEdits(minTrackDurationSeconds: Int)

sealed trait Feed
case class BeatportFeed(name : String, urlTemplate : String, filters : List[String]) extends Feed
case class TraxsourceFeed(name : String, urlTemplate : String, filters : List[String]) extends Feed

case class Config(
   previewsFolder: String,
   my: My,
   onlyNew: OnlyNew,
   noShit: NoShit,
   noCompilations: NoCompilations,
   noEdits: NoEdits,
   feeds : List[Feed])

object Config {

  implicit val feedDecoder: Decoder[Feed] = new Decoder[Feed] {
    final def apply(c: HCursor): Decoder.Result[Feed] =
      for {
        name <- c.downField("name").as[String]
        provider <- c.downField("provider").as[String]
        urlTemplate <- c.downField("urlTemplate").as[String]
        filters <- c.downField("filters").as[List[String]]
      } yield {
        provider match {
          case "beatport" => BeatportFeed(name, urlTemplate, filters)
          case "traxsource" => TraxsourceFeed(name, urlTemplate, filters)
        }
      }
  }

  implicit val configDecoder: Decoder[Config] = new Decoder[Config] {
    final def apply(c: HCursor): Decoder.Result[Config] =
      for {
        previewsFolder <- c.downField("previewsFolder").as[String]
        my <- c.downField("filters").downField("my").as[My]
        onlyNew <- c.downField("filters").downField("onlyNew").as[OnlyNew]
        noShit <- c.downField("filters").downField("noShit").as[NoShit]
        noCompilations <- c.downField("filters").downField("noCompilations").as[NoCompilations]
        noEdits <- c.downField("filters").downField("noEdits").as[NoEdits]
        feeds <- c.downField("feeds").as[List[Feed]]
      } yield {
        Config(previewsFolder, my, onlyNew, noShit, noCompilations, noEdits, feeds)
      }
  }

}
