package pd2.data

import pd2.data.TrackTable.Track
import zio.{Exit, Has, IO, URIO, ZIO, ZLayer, ZManaged}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.{GetResult, JdbcProfile}
import zio.Exit.{Failure, Success}

class TrackRepositoryLive(val db: DatabaseProvider, val profile : JdbcProfile)
  extends TrackRepository.Service {
  import profile.api._

  private val items = TrackTable.table

  def createSchema: DBIO[Unit] = items.schema.create

  def getById(id: Int): DBIO[Track] = items.filter(_.id === id).result.head

  def insertSeq(tracks: Iterable[Track]): DBIO[Option[Int]] = items ++= tracks

  def getByUniqueName(uniqueName: String): DBIO[Track] = items.filter(_.uniqueName === uniqueName).result.head

  def tryGetByUniqueName(uniqueName: String): DBIO[Option[Track]] =
    items.filter(_.uniqueName === uniqueName).result.headOption
}

object TrackRepositoryLive {
  def makeLayer : ZLayer[Has[DatabaseProvider], Throwable, TrackRepository] =
    ZLayer.fromServiceM((db:DatabaseProvider) =>
      db.profile.map { profile => new TrackRepositoryLive(db, profile) })
}
