package vine.counters

import zio._
import scala.collection.immutable.HashMap

case class CountersLive(mapRef : Ref[HashMap[String, Long]]) extends Counters.Service {

  def modify(name: String, amount: Int): ZIO[Any, Nothing, Unit] = {
    mapRef.update { map =>
      map.get(name) match {
        case Some(l)  => map.updated(name, l + amount)
        case None     => map.updated(name, amount)
      }
    }
  }

  def ensureAllZero: Task[Unit] = {
    for {
      map     <- mapRef.get
      nonZero = map.filter { case (_, v) => v != 0 }.toList
      msg     = "Non zero counters: " + nonZero.sortBy(_._1).map { case (k,v) => s"$k: $v"}.mkString(", ")
      _       <- ZIO.die(new IllegalStateException(msg)).when(nonZero.nonEmpty)
    } yield ()
  }
}

object CountersLive {
  def makeLayer: ZLayer[Any, Nothing, Counters] =
    (for {
      mapRef <- Ref.make(HashMap[String, Long]())
    } yield CountersLive(mapRef)).toLayer
}
