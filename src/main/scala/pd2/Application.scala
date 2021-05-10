package pd2
import pd2.config.ConfigService
import zio.console.putStrLn
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.{ExitCode, URIO, ZIO}

object Application extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    ZIO.succeed(()).exitCode

  }
}
