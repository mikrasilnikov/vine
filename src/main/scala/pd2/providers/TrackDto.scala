package pd2.providers

import pd2.data.{Track, TrackParsing}
import pd2.data.TrackParsing.rewriteTrackName
import zio.{URIO, ZIO}

import java.time.{Duration, LocalDate, LocalDateTime}

case class TrackDto(
  artist      : String,
  title       : String,
  label       : String,
  releaseName : String,
  releaseDate : LocalDate,
  duration    : Duration,
  feed        : String,
  internalId  : Int)
{
  val uniqueNameOption: Option[String] = TrackParsing.getUniqueNameOption(artist, title)

  val uniqueNameZio: URIO[Any, String] =
    ZIO.fromOption(uniqueNameOption)
      .orDieWith(_ => new IllegalStateException(s"Could not produce uniqueNameOption for ${artist} - ${title}"))

  def toDbTrack(runId : Option[LocalDateTime]) : Option[Track] = {
    uniqueNameOption.map(name =>
      Track(artist, title, name, Some(label), Some(releaseDate), Some(feed), runId))
  }

  def toDbTrackZio(runId : Option[LocalDateTime]): URIO[Any, Track] =
    ZIO.fromOption(toDbTrack(runId))
      .orDieWith(_ => new IllegalStateException(s"Could not produce uniqueNameOption for ${artist} - ${title}"))
}
