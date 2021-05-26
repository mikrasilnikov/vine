package pd2.data

import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.JdbcBackend
import zio.clock.Clock
import zio.duration.durationInt
import zio.{Schedule, Semaphore, Task, ZIO}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Database {
  val backendDb: JdbcBackend#Database
  /** Sqlite не любит параллельные транзакции и время от времени падает с ошибкой SQLITE_BUSY.
   *  Пожалуй в этом случае лучше просто не пытаться писать в базу параллельно. */
  val accessSemaphore : Semaphore

  final def run[R](a: DBIOAction[R, NoStream, Nothing]): Task[R] =
    accessSemaphore.withPermit(ZIO.fromFuture(_ => backendDb.run(a)))
}
