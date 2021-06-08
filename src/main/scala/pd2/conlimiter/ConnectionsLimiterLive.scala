package pd2.conlimiter
import sttp.model.Uri
import zio._
import scala.collection.immutable

case class ConnectionsLimiterLive(
  private[conlimiter] val globalSemaphore     : Semaphore,
  private[conlimiter] val perDomainLimit      : Int,
  private[conlimiter] val domainSemaphoresRef : RefM[immutable.HashMap[String, Semaphore]]
) extends ConnectionsLimiter.Service {

  def withPermit[R, A](uri: Uri)(zio: RIO[R, A]): RIO[R, A] = {
    for {
      domainSem <- getDomainSemaphore(uri)
      res <- globalSemaphore.withPermit(domainSem.withPermit(zio))
    } yield res
  }

  private[conlimiter] def getDomainSemaphore(uri : Uri) : Task[Semaphore] = {
    for {
      domain  <- getDomain(uri)
      res     <- domainSemaphoresRef.modify { map =>
        map.get(domain) match {
          case Some(sem) => ZIO.succeed((sem, map))
          case None => Semaphore.make(perDomainLimit).map(sem => (sem, map + (domain -> sem)))
        }
      }
    } yield res
  }

  private def getDomain(uri : Uri) : Task[String] = {
    uri.host match {
      case None => ZIO.fail(new IllegalStateException(s"Relative Uris are not supported"))
      case Some(h) => ZIO.succeed(h.split('.').takeRight(2).mkString("."))
    }
  }
}

object ConnectionsLimiterLive {
  def makeLayer(globalLimit : Int, perHostLimit : Int)
  : ZLayer[Any, Nothing, ConnectionsLimiter] =
    (
      for {
        globalSem <- Semaphore.make(globalLimit)
        stateRef  <- RefM.make(immutable.HashMap[String, Semaphore]())
      } yield ConnectionsLimiterLive(globalSem, perHostLimit, stateRef)
    ).toLayer
}