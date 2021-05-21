package pd2

import pd2.data.TrackTable.Track
import slick.interop.zio.DatabaseProvider
import zio.{Has, IO, ZLayer}
import zio.macros.accessible

package object data {

  type TrackRepository = Has[TrackRepository.Service]

  @accessible
  object TrackRepository {
    trait Service {
      def createSchema : IO[Throwable, Unit]
      def getById(id : Int) : IO[Throwable, Option[Track]]
      def getByUniqueName(uniqueName : String) : IO[Throwable, Option[Track]]
      def insertSeq(tracks : Iterable[Track]) : IO[Throwable, Option[Int]]
    }
  }
}
