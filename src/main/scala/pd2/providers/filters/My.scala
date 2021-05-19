package pd2.providers.filters

import pd2.config.Config
import pd2.config.ConfigDescription.FilterTag
import pd2.providers.TrackDto
import zio.ZIO

object My {

  val nullFilter = new TrackFilter[FilterTag.My.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with Config, E, Boolean] = ZIO.succeed(true)
  }

  implicit val myFilter = new TrackFilter[FilterTag.My.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with Config, E, Boolean] = {
      for {
        artists   <- Config.myArtists
        labels    <- Config.myLabels
        myArtist  =  artists.exists(a => dto.artist.toLowerCase.contains(a.toLowerCase)) ||
                     artists.exists(a => dto.title.toLowerCase.contains(a.toLowerCase))
        myLabel   =  labels.exists(s => s.toLowerCase.contains(dto.label.toLowerCase))
      } yield myArtist || myLabel
    }
  }
}
