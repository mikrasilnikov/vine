package pd2.config
import argonaut.Parse
import zio.blocking.Blocking
import zio.{Has, UIO, ZIO, ZLayer}
import zio.nio.core.file._
import zio.nio.file._
import zio.macros.accessible

import java.nio.charset.StandardCharsets

@accessible
object ConfigService {
  type Config1 = Has[ConfigService.Service]

  trait Service {
    val configuration : pd2.config.Config
  }

  val live: ZLayer[Blocking, Exception, Has[Service]] = (for {
    jsonString <- Files.readAllBytes(Path("config.json"))
      .map(c => new String(c.toArray, StandardCharsets.UTF_8))
    json <- ZIO.fromEither(Parse.parse(jsonString))
      .mapError(s => new Exception(s))
    config <- ZIO.fromEither(Config.ConfigCodec.decodeJson(json).result)
      .mapError { case (s, _) => new Exception(s) }
  } yield new Service { val configuration: Config = config })
    .toLayer
}
