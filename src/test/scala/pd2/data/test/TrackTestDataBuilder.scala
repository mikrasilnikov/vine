package pd2.data.test

import pd2.data.test.TestDataBuilder.genString
import pd2.data.{DatabaseService, Track, TrackParsing}
import zio.{Has, ZIO}

import java.time.{LocalDate, LocalDateTime}

//noinspection TypeAnnotation
case class TrackTestDataBuilder(
  artist      : Option[String],
  title       : Option[String],
  //uniqueName  : Option[String],
  label       : Option[Option[String]],
  releaseDate : Option[Option[LocalDate]],
  feed        : Option[Option[String]],
  queued      : Option[Option[LocalDateTime]],
) extends TestDataBuilder[Track] {

  def withArtist(value : String) = this.copy(artist = Some(value))
  def withTitle(value : String) = this.copy(title = Some(value))
  def withLabel(value : String) = this.copy(label = Some(Some(value)))
  def withoutLabel = this.copy(label = Some(None))
  def withReleaseDate(value : LocalDate) = this.copy(releaseDate = Some(Some(value)))
  def withoutReleaseDate = this.copy(releaseDate = Some(None))
  def withFeed(value : String) = this.copy(feed = Some(Some(value)))
  def withoutFeed = this.copy(feed = Some(None))
  def withQueued(value : LocalDateTime) = this.copy(queued = Some(Some(value)))
  def withoutQueued = this.copy(queued = Some(None))

  def build : ZIO[Has[DatabaseService], Throwable, Track] = {

    val resArtist     = artist.fold(genString[Track]("Artist"))(identity)
    val resTitle      = title.fold(genString[Track]("Title"))(identity)
    val resUniqueName = TrackParsing.getUniqueNameOption(resArtist, resTitle).get
    val resLabel      = label.fold[Option[String]](Some(genString[Track]("Label")))(identity)
    val resReleaseDate= releaseDate.fold[Option[LocalDate]](Some(LocalDate.parse("2020-01-01")))(identity)
    val resFeed       = feed.fold[Option[String]](Some(genString[Track]("Feed")))(identity)
    val resQueued     = queued.fold[Option[LocalDateTime]](None)(identity)

    val entity = Track(resArtist, resTitle, resUniqueName, resLabel, resReleaseDate, resFeed, resQueued)

    ZIO.service[DatabaseService].flatMap { db =>
      import db.profile.api._
      for {
        newId <- db.run(db.tracks returning db.tracks.map(_.id) += entity)
      } yield entity.copy(id = newId)
    }
  }
}

object TrackTestDataBuilder {
  def empty: TrackTestDataBuilder = TrackTestDataBuilder(None, None, None, None, None, None)
}
