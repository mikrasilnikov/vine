package pd2.providers

import pd2.config.ConfigDescription.FilterTag
import pd2.config.{Config, FilterTag}
import pd2.data.TrackRepository
import pd2.helpers.Conversions.OptionToZio
import zio.ZIO

package object filters {

  type FilterEnv = Config with TrackRepository

  trait TrackFilter[F <: FilterTag] {
    def filter[R, E](dto : TrackDto) : ZIO[R with FilterEnv, E, Boolean]
  }

  implicit val empty = new TrackFilter[FilterTag.Empty.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with FilterEnv, E, Boolean] = ZIO.succeed(true)
  }

  implicit val my = new TrackFilter[FilterTag.My.type] {
    override def filter[R, E](dto: TrackDto): ZIO[R with FilterEnv, E, Boolean] = {
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
    override def filter[R, E](dto: TrackDto): ZIO[R with FilterEnv, E, Boolean] = {

      for {
        _ <- ZIO.succeed()
        _ <- Config.myArtists
        _ <- TrackRepository.createSchema
        //uniqueName <- ZIO.fromOption(dto.uniqueName).orElseFail(new Exception)
        //exists <- TrackRepository.getByUniqueName(uniqueName)
      } yield true

    }
  }


}
