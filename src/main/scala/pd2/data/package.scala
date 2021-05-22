package pd2

import pd2.data.TrackTable.Track
import slick.dbio.DBIO
import slick.interop.zio.DatabaseProvider
import zio.{Has, IO, ZIO, ZLayer}
import zio.macros.accessible

package object data {

  type TrackRepository = Has[TrackRepository.Service]
  @accessible
  object TrackRepository {
    trait Service {
      def createSchema : DBIO[Unit]
      def getById(id : Int) : DBIO[Track]
      def tryGetByUniqueName(uniqueName : String) : DBIO[Option[Track]]
      def getByUniqueName(uniqueName : String) : DBIO[Track]
      def insertSeq(tracks : Iterable[Track]) : DBIO[Option[Int]]
    }
  }

  type DbProvider = Has[slick.interop.zio.DatabaseProvider]
}
