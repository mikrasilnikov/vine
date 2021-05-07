package pd2.config
import argonaut.Parse
import zio.{Has, ZIO, ZLayer}
import zio.nio.core.file._
import zio.nio.file._
import zio.macros.accessible

import java.nio.charset.StandardCharsets

@accessible
object ConfigService {
  type Config = Has[ConfigService.Service]

  trait Service {
    val config : Config
  }

  val live = (for {
    jsonString <- Files.readAllBytes(Path("config.json"))
      .map(c => new String(c.toArray, StandardCharsets.UTF_8))
    json <- ZIO.fromEither(Parse.parse(jsonString))
      .mapError(s => new Exception(s))
    config <- ZIO.fromEither(Config.ConfigCodec.decodeJson(json).result)
      .mapError { case (s, _) => new Exception(s) }
  } yield config).toLayer
}
