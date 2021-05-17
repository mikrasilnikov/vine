package pd2
import pd2.config.{ConfigService, TraxsourceFeed}
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.ui.ProgressBar.ProgressBarDimensions
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import sttp.client3.httpclient.zio.SttpClient
import zio.console.{Console, putStr, putStrLn}
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.{ExitCode, Ref, Schedule, URIO, ZIO, clock}
import zio.duration.durationInt
import zio.system.System

import java.time.LocalDate

object Application extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val feed1 = TraxsourceFeed(
      "03-traxsource-tech-all",
      "/genre/18/tech-house/all?cn=tracks&ipp=100&period={0},{1}&gf=18&ob=r_date&so=asc",
      List())

    val feed2 = TraxsourceFeed(
      "03-traxsource-deep-all",
      "/genre/13/deep-house/all?cn=tracks&ipp=100&period={0},{1}&gf=4&ob=r_date&so=asc",
      List())

    val effect = for {
      resRef <- Ref.make(List[Int]())

      fiber1 <- Traxsource.processTracks(
            feed1,
            LocalDate.parse("2021-04-01"),
            LocalDate.parse("2021-04-02"),
            _ => resRef.modify(l => ((), 1 :: l)).unit *> zio.clock.sleep(50.millis)).fork

      fiber2 <- Traxsource.processTracks(
            feed2,
            LocalDate.parse("2021-04-01"),
            LocalDate.parse("2021-04-02"),
            _ => resRef.modify(l => ((), 1 :: l)).unit *> zio.clock.sleep(50.millis)).fork

      progressFiber <- clock.sleep(1000.millis) *>
                       ConsoleProgress.drawProgress.repeat(Schedule.duration(1.second)).forever.fork

      _   <- fiber1.join *> fiber2.join
      _   <- clock.sleep(1.second) *> progressFiber.interrupt
      res <- resRef.get
      _   <- putStrLn(s"\ntotal tracks: ${res.length}")
    } yield ()

    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val consoleProgress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(ProgressBarDimensions(25, 65))
    val sttpClient = HttpClientZioBackend.layer()
    val traxsourceLive = TraxsourceLive.makeLayer(8)

    val customLayer = (consoleProgress ++ sttpClient) >>> traxsourceLive ++ consoleProgress

    effect.provideCustomLayer(customLayer).exitCode
  }
}
