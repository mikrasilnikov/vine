package pd2.data.test

import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.{JdbcBackend, JdbcProfile}
import zio.{Has, Task, ZIO, ZLayer, ZManaged}
import zio.nio.core.file.Path

import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

object TestBackend {

  @volatile
  private var currentDbNum = 1
  private val connections = new ConcurrentHashMap[Int, Connection]

  def makeLayer : ZLayer[Any, Throwable, Has[JdbcBackend#Database]] = {

    var current = 0
    this.synchronized {
      current = currentDbNum
      currentDbNum += 1
    }

    val create : Task[JdbcBackend#Database] = ZIO.effect {
        println(s"Creating database db$current")
        val dataSource = new org.h2.jdbcx.JdbcDataSource
        dataSource.setURL(s"jdbc:h2:mem:db$current")

        val conn = dataSource.getConnection
        connections.put(current, conn)

        slick.jdbc.H2Profile.backend.Database.forDataSource(dataSource, maxConnections = None)
      }

    def release(db : JdbcBackend#Database) =
      ZIO.succeed {
        println(s"Releasing database db$current")
        connections.get(current).close()
        connections.remove(current)
        db.close()
      }

    ZManaged.make(create)(release).toLayer
  }
}
