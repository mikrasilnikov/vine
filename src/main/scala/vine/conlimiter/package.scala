package vine

import sttp.model.Uri
import zio.{Has, RIO, Semaphore, Task, ZIO}
import zio.macros.accessible
import zio.system._

package object conlimiter {

  type ConnectionsLimiter = Has[ConnectionsLimiter.Service]

  @accessible
  object ConnectionsLimiter {

    /** Connections limiter service for http requests. Live implementation enforces limits for
     * total simultaneous requests and simultaneous requests per host.*/
    trait Service {
      def withPermit[R, A](uri : Uri)(zio : RIO[R, A]) : RIO[R, A]
    }
  }
}
