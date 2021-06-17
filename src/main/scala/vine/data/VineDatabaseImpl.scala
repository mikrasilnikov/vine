package vine.data

import zio._
import zio.logging._
import slick.dbio._
import slick.jdbc._
import java.time._
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global

case class VineDatabaseImpl(
  profile: JdbcProfile,
  backendDb: JdbcBackend#Database,
  accessSemaphore : Semaphore) {
  import profile.api._

  val tracksTableName = "tracks"

  //noinspection MutatorLikeMethodIsParameterless
  class Tracks(tag: Tag) extends Table[Track](tag, tracksTableName) {

    def artist = column[String]("Artist")
    def title = column[String]("Title")
    def uniqueName = column[String]("UniqueName")
    def label = column[Option[String]]("Label")
    def releaseDate = column[Option[LocalDate]]("ReleaseDate")
    def feed = column[Option[String]]("Feed")
    def queued = column[Option[LocalDateTime]]("Queued")
    def id = column[Int]("Id", O.PrimaryKey, O.AutoInc)
    def * = (artist, title, uniqueName, label, releaseDate, feed, queued, id).mapTo[Track]

    def uniqueNameIndex = index("ixUniqueName", uniqueName, unique = true)

  }

  val tracks = TableQuery[Tracks]

  def createSchemaIfNotExists : ZIO[VineDatabase with Logging, Throwable, Unit] = {
      ZIO.service[VineDatabaseImpl].flatMap { db =>
        for {
          exists <- db.run(sql"SELECT name FROM sqlite_master WHERE type='table' AND name=$tracksTableName".as[String])
          _      <- db.run(tracks.schema.create).unless(exists.nonEmpty)
        } yield ()
      }
  }

  final def run[R](a: DBIOAction[R, NoStream, Effect.All]): Task[R] =
    accessSemaphore.withPermit(ZIO.fromFuture(_ => backendDb.run(a))).retryWhile {
      case e : SQLException => e.getMessage.contains("[SQLITE_BUSY]")
      case _ => false
    }

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

object VineDatabaseImpl {
  def makeLayer(profile: JdbcProfile) : ZLayer[Has[JdbcBackend#Database], Nothing, VineDatabase] = {
    val make = ZIO.service[JdbcBackend#Database].flatMap { backend =>
      for {
        semaphore <- Semaphore.make(1)
      } yield VineDatabaseImpl(profile, backend, semaphore)
    }
    make.toLayer
  }
}

