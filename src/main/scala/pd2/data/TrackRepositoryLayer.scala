package pd2.data

import pd2.data.TrackTable.Track
import zio.{Has, IO, UIO, ZIO, ZLayer}
import zio.macros.accessible
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._

@accessible
object TrackRepositoryLayer {
  type TrackRepository = Has[TrackRepositoryLayer.Service]

  trait Service {
    def createSchema : IO[Throwable, Unit]
    def getById(id : Int) : IO[Throwable, Option[Track]]
    def insertSeq(tracks : Iterable[Track]) : IO[Throwable, Option[Int]]
  }

  val live : ZLayer[Has[DatabaseProvider], Throwable, Has[TrackRepositoryLayer.Service]] =
    ZLayer.fromServiceM((db:DatabaseProvider) =>
      db.profile.map { profile => new TrackRepositoryLive(db, profile) })
}
