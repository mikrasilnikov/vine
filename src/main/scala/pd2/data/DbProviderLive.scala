package pd2.data

import com.typesafe.config.ConfigFactory
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio.{Has, ZLayer}
import zio.nio.core.file.Path

object DbProviderLive {
  def makeLayer(sqliteDbPath : Path): ZLayer[Any, Throwable, Has[DatabaseProvider]] = {
      val configMap = new java.util.HashMap[String, String]
      configMap.put("driver", "org.sqlite.JDBC")
      configMap.put("url", s"jdbc:sqlite:${sqliteDbPath.toString}")
      configMap.put("connectionPool", "disabled")

      val config = ConfigFactory.parseMap(configMap)

      val dbProfile = ZLayer.succeed(slick.jdbc.SQLiteProfile.asInstanceOf[JdbcProfile])
      val dbConfig = ZLayer.succeed(config)

      (dbConfig ++ dbProfile) >>> DatabaseProvider.live
    }
}
