package pd2.data

import pd2.data.TrackTable.Track
import zio.{Has, IO, ZIO}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.JdbcProfile

class TrackRepositoryLive(private val db: DatabaseProvider, private val profile : JdbcProfile) extends TrackRepository.Service {

  import profile.api._
  private val items = TrackTable.table

  override def createSchema: IO[Throwable, Unit] = {
    val query = items.schema.create
    ZIO.fromDBIO(query).provide(Has(db))
  }

  override def getById(id: Int): IO[Throwable, Option[Track]] = {
    val query = items.filter(_.id === id).result
    ZIO.fromDBIO(query).map(_.headOption).provide(Has(db))
  }

  override def insertSeq(tracks: Iterable[Track]): IO[Throwable, Option[Int]] = {
    ZIO.fromDBIO(items ++= tracks).provide(Has(db))
  }


}
