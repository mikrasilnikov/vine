package pd2.data

import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcBackend
import zio.clock.Clock
import zio.duration.durationInt
import zio.{Schedule, Semaphore, Task, ZIO}

import java.sql.SQLException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Database {
  val backendDb: JdbcBackend#Database
  /** Sqlite не любит параллельные транзакции и время от времени падает с ошибкой SQLITE_BUSY.
   *  Пожалуй в этом случае лучше просто не пытаться писать в базу параллельно. */
  val accessSemaphore : Semaphore

  final def run[R](a: DBIOAction[R, NoStream, Effect.All]): Task[R] =
    accessSemaphore.withPermit(ZIO.fromFuture(_ => backendDb.run(a))).retryWhile {
      case e : SQLException => e.getMessage.contains("[SQLITE_BUSY]")
      case _ => false
    }
}
