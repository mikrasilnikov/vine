package pd2
import pd2.config.ConfigService
import zio.console.putStrLn
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.{ExitCode, URIO, ZIO}

import scala.reflect.io.File

object Application extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val app = for {
      //currentDirectory <- ZIO.effect(File(".").toAbsolute)
      config <- ConfigService.configuration
      _ <- putStrLn(config.feeds.toString())
    } yield ()

    app.provideCustomLayer(ConfigService.live).exitCode

  }
}
