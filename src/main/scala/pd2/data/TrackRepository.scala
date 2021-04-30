package pd2.data

import pd2.data.TrackTable.Track
import zio.{Has, IO, ZIO, ZLayer}
import zio.macros.accessible
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._

@accessible
object TrackRepository {
  type TrackRepository = Has[TrackRepository.Service]

  trait Service {
    def getById(id : Int) : IO[Throwable, Option[Track]]
  }

  val live : ZLayer[Has[DatabaseProvider], Throwable, Has[TrackRepository.Service]] =
    ZLayer.fromServiceM((db:DatabaseProvider) => {
      db.profile.map { profile =>
        import profile.api._

        new Service {
          private val items = TrackTable.table

          override def getById(id: Int): IO[Throwable, Option[Track]] = {
            val query = items.filter(_.id === id).result
            ZIO.fromDBIO(query).map(_.headOption).provide(Has(db))
          }
        }

      }
    })
}
