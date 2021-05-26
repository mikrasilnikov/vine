package pd2.providers

import pd2.config.ConfigDescription.FilterTag
import pd2.config.{Config, FilterTag}
import pd2.data.Pd2Database
import pd2.helpers.Conversions.OptionToZio
import zio.{Has, ZIO}

package object filters {

  type FilterEnv = Config with Pd2Database

  trait TrackFilter[F <: FilterTag] {
    def filter[R, E](dto : TrackDto) : ZIO[R with Config with Has[Pd2Database], E, Boolean]
  }

  implicit val empty = new TrackFilter[FilterTag.Empty.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with Config with Has[Pd2Database], E, Boolean] = ZIO.succeed(true)
  }

  implicit val my = new TrackFilter[FilterTag.My.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with Config with Has[Pd2Database], E, Boolean] = {
      for {
        artists   <- Config.myArtists
        labels    <- Config.myLabels
        myArtist  =  artists.exists(a => dto.artist.toLowerCase.contains(a.toLowerCase)) ||
                     artists.exists(a => dto.title.toLowerCase.contains(a.toLowerCase))
        myLabel   =  labels.exists(l => l.toLowerCase == dto.label.toLowerCase)
      } yield myArtist || myLabel
    }
  }

  val onlyNew = new TrackFilter[FilterTag.OnlyNew.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with Config with Has[Pd2Database], E, Boolean] = {
      for {
        _ <- ZIO.succeed()
        _ <- Config.myArtists
        //_ <- TrackRepository.createSchema
        //uniqueName <- dto.uniqueName.toZio
        //exists <- TrackRepository.getByUniqueName(uniqueName)
      } yield true

    }
  }


}
