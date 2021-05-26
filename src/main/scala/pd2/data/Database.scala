package pd2.data

import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.JdbcBackend
import zio.{Task, ZIO}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Database {
  val backendDb: JdbcBackend#Database
  //final def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = backendDb.run(a)
  final def run[R](a: DBIOAction[R, NoStream, Nothing]): Task[R] = ZIO.fromFuture(_ => backendDb.run(a))
}
