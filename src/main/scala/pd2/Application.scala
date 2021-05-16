package pd2
import pd2.config.{ConfigService, TraxsourceFeed}
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.ui.ProgressBar.ProgressBarDimensions
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import sttp.client3.httpclient.zio.SttpClient
import zio.console.{Console, putStr, putStrLn}
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.{ExitCode, Ref, Schedule, URIO, ZIO}
import zio.duration.durationInt
import zio.system.System

import java.time.LocalDate

object Application extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val feed = TraxsourceFeed(
      "03-traxsource-tech-all",
      "/genre/18/tech-house/all?cn=tracks&ipp=100&period={0},{1}&gf=18&ob=r_date&so=asc",
      List())

    val effect = for {
      resRef <- Ref.make(List[Int]())
      progressFiber <- ConsoleProgress.drawProgress.repeat(Schedule.duration(333.millis)).forever.fork
      _ <- Traxsource.processTracks(
            feed,
            LocalDate.parse("2021-04-01"),
            LocalDate.parse("2021-04-02"),
            _ => resRef.modify(l => ((), 1 :: l)).unit *> zio.clock.sleep(50.millis)
          )
      _   <- zio.clock.sleep(500.millis) *> progressFiber.interrupt
      res <- resRef.get
      _   <- putStrLn(s"\ntotal tracks: ${res.length}")
    } yield ()

    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val consoleProgress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(ProgressBarDimensions(15, 50))
    val sttpClient = HttpClientZioBackend.layer()
    val traxsourceLive = TraxsourceLive.makeLayer(8)

    val customLayer = (consoleProgress ++ sttpClient) >>> traxsourceLive ++ consoleProgress

    effect.provideCustomLayer(customLayer).exitCode
  }
}
