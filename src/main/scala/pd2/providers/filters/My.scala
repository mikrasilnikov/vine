package pd2.providers.filters

import pd2.config.Config
import pd2.config.ConfigDescription.FilterTag
import pd2.providers.TrackDto
import zio.ZIO

object My {
  implicit val myFilter = new TrackFilter[FilterTag.My.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with Config, E, Boolean] = {
      for {
        artists   <- Config.myArtists
        labels    <- Config.myLabels
        myArtist  =  artists.exists(s => s.toLowerCase.contains(dto.artist.toLowerCase)) ||
                     artists.exists(s => s.toLowerCase.contains(dto.title.toLowerCase))
        myLabel   =  labels.exists(s => s.toLowerCase.contains(dto.label.toLowerCase))
      } yield myArtist || myLabel
    }
  }
}
