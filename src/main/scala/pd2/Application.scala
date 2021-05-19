package pd2
import pd2.config.ConfigDescription.Feed.TraxsourceFeed
import pd2.providers.{Pd2Exception, TrackDto}
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.ui.ProgressBar.ProgressBarDimensions
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import sttp.client3.httpclient.zio.SttpClient
import zio.console.{Console, putStr, putStrLn}
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.{Chunk, ExitCode, Has, Ref, Schedule, URIO, ZIO, clock}
import zio.duration.durationInt
import zio.system.System
import pd2.config.Config
import zio.blocking.Blocking

import java.io.File
import java.nio.file.{Files => JFiles, Path => JPath}
import java.time.LocalDate
import scala.collection.mutable.ArrayBuffer

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

    def fixPath(s : String) : String =
      s.replaceAll("([<>:\"/\\\\|?*])", "_")

    def processTrack(date: LocalDate, trackDto: TrackDto, data : Array[Byte]): ZIO[Blocking with Config, Throwable, Unit] =
      for {
        targetPath    <- Config.targetPath
        _             <- Files.createDirectory(targetPath).whenM(Files.notExists(targetPath))
        fileName      =  s"${fixPath(trackDto.artist)} - ${fixPath(trackDto.title)}.mp3"
        _             <- Files.writeBytes(targetPath / Path(fileName), Chunk.fromArray(data))
                            .whenM(pd2.providers.filters.My.myFilter.filter(trackDto))
    } yield ()

    val date1 = LocalDate.parse("2021-04-01")
    val date2 = LocalDate.parse("2021-04-02")

    val effect = for {
      resRef <- Ref.make(0)

      fiber1 <- Traxsource.processTracks(feed1, date1, date2,
            pd2.providers.filters.My.myFilter.filter,
            (dto, data) => processTrack(date1, dto, data) *> resRef.modify(i => ((), i + 1)).unit).fork

      fiber2 <- Traxsource.processTracks(feed2, date1, date2,
        pd2.providers.filters.My.myFilter.filter,
        (dto, data) => processTrack(date1, dto, data) *> resRef.modify(i => ((), i + 1)).unit).fork

      progressFiber <- clock.sleep(1000.millis) *>
                       ConsoleProgress.drawProgress.repeat(Schedule.duration(333.millis)).forever.fork

      _   <- fiber1.join *> fiber2.join
      _   <- clock.sleep(1.second) *> progressFiber.interrupt
      res <- resRef.get
      _   <- putStrLn(s"\ntotal tracks: ${res}")
    } yield ()

    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val consoleProgress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(ProgressBarDimensions(25, 65))
    val sttpClient = HttpClientZioBackend.layer()
    val traxsourceLive = TraxsourceLive.makeLayer(8)

    val configLayer = Config.makeLayer("config.json", date1)
    val customLayer = ((consoleProgress ++ sttpClient) >>> traxsourceLive) ++ consoleProgress ++ configLayer

    effect.provideCustomLayer(customLayer).exitCode
  }
}
