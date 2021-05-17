package pd2
import pd2.config.{ConfigService, TraxsourceFeed}
import pd2.providers.{Pd2Exception, TrackDto}
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.ui.ProgressBar.ProgressBarDimensions
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import sttp.client3.httpclient.zio.SttpClient
import zio.console.{Console, putStr, putStrLn}
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.{Chunk, ExitCode, Ref, Schedule, URIO, ZIO, clock}
import zio.duration.durationInt
import zio.system.System

import java.io.File
import java.nio.file.{Path => JPath, Files => JFiles}
import java.time.LocalDate
import scala.collection.mutable.ArrayBuffer

object Application extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val currentDirectory =
      new File(Application.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).getPath

    val targetDirectory = JPath.of(currentDirectory).getParent.resolve(JPath.of("previews"))
    if (!JFiles.exists(targetDirectory))
      JFiles.createDirectory(targetDirectory)

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

    def processTrack(trackDto: TrackDto) = for {
      _ <- ZIO.succeed()
      fileName = s"${fixPath(trackDto.artist)} - ${fixPath(trackDto.title)}.mp3"
      _ <- Files.writeBytes(
          Path.fromJava(
            targetDirectory.resolve(fileName)),
            Chunk.fromArray(trackDto.data))
    } yield ()

    val effect = for {
      resRef <- Ref.make(0)

      fiber1 <- Traxsource.processTracks(
            feed1,
            LocalDate.parse("2021-04-01"),
            LocalDate.parse("2021-04-02"),
            dto => processTrack(dto) *> resRef.modify(i => ((), i + 1)).unit).fork

      fiber2 <- Traxsource.processTracks(
            feed2,
            LocalDate.parse("2021-04-01"),
            LocalDate.parse("2021-04-02"),
            dto => processTrack(dto) *> resRef.modify(i => ((), i + 1)).unit).fork

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

    val customLayer = (consoleProgress ++ sttpClient) >>> traxsourceLive ++ consoleProgress

    effect.provideCustomLayer(customLayer).exitCode
  }
}
