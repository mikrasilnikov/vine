package pd2.config
import zio.blocking.Blocking
import zio.{Has, ZIO, ZLayer}
import zio.macros.accessible
import zio.nio.core.file.Path
import zio.nio.file.Files
import io.circe.parser._
import java.nio.charset.StandardCharsets

@accessible
object ConfigService {
  type Config = Has[ConfigService.Service]

  trait Service {
    val configuration : pd2.config.Config
  }

  val live: ZLayer[Blocking, Exception, Has[Service]] = {
    for {
      jsonString <- Files.readAllBytes(Path("config.json")).map(c => new String(c.toArray, StandardCharsets.UTF_8))
      json <- ZIO.fromEither(parse(jsonString)).mapError(s => new Exception(s))
      config <- ZIO.fromEither(json.as[pd2.config.Config])
    } yield new Service { val configuration : pd2.config.Config = config }
  }.toLayer
}
