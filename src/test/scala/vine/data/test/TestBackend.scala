package vine.data.test

import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.{JdbcBackend, JdbcProfile}
import slick.util.AsyncExecutor
import zio.{Has, Task, ZIO, ZLayer, ZManaged}
import zio.nio.core.file.Path

import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

object TestBackend {

  @volatile
  private var currentDbNum = 0
  private val connections = new ConcurrentHashMap[Int, Connection]

  def makeLayer : ZLayer[Any, Throwable, Has[JdbcBackend#Database]] = {

    val current =
      this.synchronized {
        currentDbNum += 1
        currentDbNum
      }

    val create : Task[JdbcBackend#Database] = ZIO.effect {
        val dataSource = new org.h2.jdbcx.JdbcDataSource
        dataSource.setURL(s"jdbc:h2:mem:db$current")

        val conn = dataSource.getConnection
        connections.put(current, conn)

        val executor = AsyncExecutor("testExecutor", minThreads = 4, maxThreads = 4, queueSize = 8, maxConnections = 4)
        slick.jdbc.H2Profile.backend.Database.forDataSource(dataSource, maxConnections = Some(4), executor)
      }

    def release(db : JdbcBackend#Database) =
      ZIO.succeed {
        connections.get(current).close()
        connections.remove(current)
        db.close()
      }

    ZManaged.make(create)(release).toLayer
  }
}
