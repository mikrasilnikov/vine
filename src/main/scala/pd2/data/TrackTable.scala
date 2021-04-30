package pd2.data

import java.time.LocalDate
import slick.jdbc.SQLiteProfile.api._

object TrackTable {

  case class Track(
    artist: String,
    title: String,
    uniqueName: String,
    label: Option[String],
    releaseDate: Option[LocalDate],
    feed: Option[String],
    id: Int = 0)

  //noinspection MutatorLikeMethodIsParameterless
  class Tracks(tag: Tag) extends Table[Track](tag, "tracks") {
    def id          = column[Int]("Id", O.PrimaryKey, O.AutoInc)
    def artist      = column[String]("Artist")
    def title       = column[String]("Title")
    def uniqueName = column[String]("UniqueName")
    def label       = column[Option[String]]("Label")
    def releaseDate = column[Option[LocalDate]]("ReleaseDate")
    def feed        = column[Option[String]]("Feed")

    def * = (artist, title, uniqueName, label, releaseDate, feed, id).mapTo[Track]

    def uniqueNameIndex = index("ixUniqueName", uniqueName, unique = true)
  }

  val table = TableQuery[Tracks]

}

