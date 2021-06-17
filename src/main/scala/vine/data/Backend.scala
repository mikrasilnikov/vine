package vine.data

import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.{JdbcBackend, JdbcProfile}
import zio.nio.core.file.Path
import zio.{Has, ZIO, ZLayer, ZManaged}

object Backend {
  def makeLayer(profile : JdbcProfile, config: Config) : ZLayer[Any, Throwable, Has[JdbcBackend#Database]] =
    ZManaged.make(ZIO.effect(profile.backend.Database.forConfig("", config)))(db => ZIO.succeed(db.close()))
      .toLayer

  def makeSqliteLiveConfig(path : Path) : Config = {
    val configMap = new java.util.HashMap[String, String]
    configMap.put("driver", "org.sqlite.JDBC")
    configMap.put("url", s"jdbc:sqlite:$path")
    configMap.put("connectionPool", "disabled")
    configMap.put("connectionInitSql", "PRAGMA journal_mode = WAL;PRAGMA synchronous = NORMAL")

    ConfigFactory.parseMap(configMap)
  }
}

