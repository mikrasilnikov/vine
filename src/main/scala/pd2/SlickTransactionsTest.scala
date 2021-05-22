package pd2

import zio.{ExitCode, URIO, ZIO}
import pd2.data._
import zio.console._
import zio.nio.core.file.Path

object SlickTransactionsTest extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val dbEffect = for {
      track1  <- TrackRepository.getById(5)
      _       <- putStrLn(track1.toString)
     // _       <- ZIO.fail(new Exception("!"))
      track2  <- TrackRepository.getById(110)
      _       <- putStrLn(track2.toString)
    } yield ()

    val transactional = dbEffect

    val trackRepository = DbProviderLive.makeLayer(Path("c:\\!temp\\imported.db")) >>> TrackRepositoryLive.makeLayer

    transactional.provideCustomLayer(trackRepository).exitCode

  }
}
