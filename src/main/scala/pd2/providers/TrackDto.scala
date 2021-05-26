package pd2.providers

import pd2.data.{Track, TrackParsing}
import pd2.data.TrackParsing.rewriteTrackName
import zio.{URIO, ZIO}

import java.time.{LocalDate, LocalDateTime}

case class TrackDto(
  artist : String,
  title : String,
  label : String,
  releaseDate : LocalDate,
  feed : String,
  internalId : Int)
{
  val uniqueNameOpt: Option[String] = for {
    artists <- TrackParsing.parseArtists(artist)
    title <- TrackParsing.parseTitle(title)
  } yield rewriteTrackName(artists, title)

  val uniqueNameZio: URIO[Any, String] =
    ZIO.fromOption(uniqueNameOpt)
      .orDieWith(_ => new IllegalStateException(s"Could not produce uniqueNameOpt for ${artist} - ${title}"))

  def toDbTrack(runId : Option[LocalDateTime]) : Option[Track] = {
    uniqueNameOpt.map(name =>
      Track(artist, title, name, Some(label), Some(releaseDate), Some(feed), runId))
  }

  def toDbTrackZio(runId : Option[LocalDateTime]): URIO[Any, Track] =
    ZIO.fromOption(toDbTrack(runId))
      .orDieWith(_ => new IllegalStateException(s"Could not produce uniqueNameOpt for ${artist} - ${title}"))
}
