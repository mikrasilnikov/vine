package pd2.data

import slick.jdbc.{JdbcBackend, JdbcProfile}
import zio.{Has, ZIO, ZLayer}
import java.time.LocalDate

case class Pd2Database(profile: JdbcProfile, override val backendDb: JdbcBackend#Database) extends Database {
  import profile.api._

  //noinspection MutatorLikeMethodIsParameterless
  class Tracks(tag: Tag) extends Table[Track](tag, "tracks") {
    def id          = column[Int]("Id", O.PrimaryKey, O.AutoInc)
    def artist      = column[String]("Artist")
    def title       = column[String]("Title")
    def uniqueName  = column[String]("UniqueName")
    def label       = column[Option[String]]("Label")
    def releaseDate = column[Option[LocalDate]]("ReleaseDate")
    def feed        = column[Option[String]]("Feed")

    def * = (artist, title, uniqueName, label, releaseDate, feed, id).mapTo[Track]

    def uniqueNameIndex = index("ixUniqueName", uniqueName, unique = true)
  }

  val tracks = TableQuery[Tracks]
}

case class Track(
  artist: String,
  title: String,
  uniqueName: String,
  label: Option[String],
  releaseDate: Option[LocalDate],
  feed: Option[String],
  id: Int = 0)

object Pd2Database {
  def makeLayer(profile: JdbcProfile) : ZLayer[Has[JdbcBackend#Database], Nothing, Has[Pd2Database]] = {
    ZLayer.fromService[JdbcBackend#Database, Pd2Database](db => Pd2Database(profile, db))
  }
}

