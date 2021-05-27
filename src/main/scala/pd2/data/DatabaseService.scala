package pd2.data

import slick.jdbc.{JdbcBackend, JdbcProfile}
import zio.{Has, Semaphore, ZIO, ZLayer}

import java.time.{LocalDate, LocalDateTime}

case class DatabaseService(
  profile: JdbcProfile,
  override val backendDb: JdbcBackend#Database,
  override val accessSemaphore : Semaphore) extends Database {
  import profile.api._

  //noinspection MutatorLikeMethodIsParameterless
  class Tracks(tag: Tag) extends Table[Track](tag, "tracks") {

    def artist      = column[String]("Artist")
    def title       = column[String]("Title")
    def uniqueName  = column[String]("UniqueName")
    def label       = column[Option[String]]("Label")
    def releaseDate = column[Option[LocalDate]]("ReleaseDate")
    def feed        = column[Option[String]]("Feed")
    def queued      = column[Option[LocalDateTime]]("Queued")
    def id          = column[Int]("Id", O.PrimaryKey, O.AutoInc)

    def * = (artist, title, uniqueName, label, releaseDate, feed, queued, id).mapTo[Track]

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
  queued : Option[LocalDateTime],
  id: Int = 0)

object DatabaseService {
  def makeLayer(profile: JdbcProfile) : ZLayer[Has[JdbcBackend#Database], Nothing, Has[DatabaseService]] = {
    val make = ZIO.service[JdbcBackend#Database].flatMap { backend =>
      for {
        semaphore <- Semaphore.make(1)
      } yield DatabaseService(profile, backend, semaphore)
    }
    make.toLayer
  }
}

