package pd2.config

import argonaut._
import Argonaut._

case class My(artistsFile: String, labelsFile: String)
case class OnlyNew(dataPath: String, fileTemplate: String)
case class NoShit(dataFiles : List[String])
case class NoCompilations(maxTracksPerRelease: Int)
case class NoEdits(minTrackDurationSeconds: Int)
case class Feed(name: String, provider: String, urlTemplate: String, filters: List[String])

case class Config(
   previewsFolder: String,
   my: My,
   onlyNew: OnlyNew,
   noShit: NoShit,
   noCompilations: NoCompilations,
   noEdits: NoEdits,
   feeds : List[Feed])

object Config {
  implicit def MyCodec : CodecJson[My] = casecodec2(My.apply, My.unapply)("artistsFile", "labelsFile")
  implicit def OnlyNewCodec : CodecJson[OnlyNew] = casecodec2(OnlyNew.apply, OnlyNew.unapply)("dataPath", "fileTemplate")
  implicit def NoShitCodec : CodecJson[NoShit] = casecodec1(NoShit.apply, NoShit.unapply)("dataFiles")
  implicit def NoCompilationsCodec : CodecJson[NoCompilations] = casecodec1(NoCompilations.apply, NoCompilations.unapply)("maxTracksPerRelease")
  implicit def NoEditsCodec : CodecJson[NoEdits] = casecodec1(NoEdits.apply, NoEdits.unapply)("minTrackDurationSeconds")
  implicit def FeedCodec : CodecJson[Feed] = casecodec4(Feed.apply, Feed.unapply)("name", "provider", "urlTemplate", "filters")

  implicit def ConfigCodec: DecodeJson[Config] = DecodeJson {
    c => for {
      previewsFolder  <- (c --\ "previewsFolder").as[String]
      my              <- (c --\ "filters" --\ "my").as[My]
      onlyNew         <- (c --\ "filters" --\ "onlyNew").as[OnlyNew]
      noShit          <- (c --\ "filters" --\ "noShit").as[NoShit]
      noCompilations  <- (c --\ "filters" --\ "noCompilations").as[NoCompilations]
      noEdits         <- (c --\ "filters" --\ "noEdits").as[NoEdits]
      feeds           <- (c --\ "feeds").as[List[Feed]]
    } yield Config(previewsFolder, my, onlyNew, noShit, noCompilations, noEdits, feeds)
  }
}


