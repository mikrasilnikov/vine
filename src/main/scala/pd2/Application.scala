package pd2
import pd2.config.Config
import pd2.config.ConfigDescription.Feed.TraxsourceFeed
import pd2.config.ConfigDescription.FilterTag
import pd2.data.{Backend, Pd2DatabaseService}
import pd2.providers.TrackDto
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.ui.ProgressBar.ProgressBarDimensions
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import slick.jdbc.SQLiteProfile
import zio.blocking.Blocking
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.system.System
import zio.{Chunk, ExitCode, Ref, Schedule, URIO, ZIO, clock}

import java.time.LocalDate

object Application extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val feed1 = TraxsourceFeed(
      "03-traxsource-tech-all",
      "/genre/18/tech-house/all?cn=tracks&ipp=100&period={0},{1}&gf=18",
      List())

    val feed2 = TraxsourceFeed(
      "03-traxsource-deep-all",
      "/genre/13/deep-house/all?cn=tracks&ipp=100&period={0},{1}&gf=4",
      List())

    def fixPath(s : String) : String =
      s.replaceAll("([<>:\"/\\\\|?*])", "_")

    def processTrack(trackDto: TrackDto, data : Array[Byte]): ZIO[Blocking with Config, Throwable, Unit] =
      for {
        targetPath    <- Config.targetPath
        fileName      =  s"${fixPath(trackDto.artist)} - ${fixPath(trackDto.title)}.mp3"
        _             <- Files.writeBytes(targetPath / Path(fileName), Chunk.fromArray(data))
    } yield ()

    val date1 = LocalDate.parse("2021-04-01")
    val date2 = LocalDate.parse("2021-04-02")

    val effect = for {
      targetPath    <- Config.targetPath
      _             <- Files.createDirectory(targetPath).whenM(Files.notExists(targetPath))

      receivedDtos <- Ref.make(List[TrackDto]())

      progressFiber <- ConsoleProgress.drawProgress.repeat(Schedule.fixed(500.millis)).forever.fork

      fiber1 <- Traxsource.processTracks(feed1, date1, date2,
                  /*pd2.providers.filters.my ++*/ pd2.providers.filters.onlyNew,
                  (dto, data) => processTrack(dto, data) *> receivedDtos.update(dto :: _)).fork

      fiber2 <- Traxsource.processTracks(feed2, date1, date2,
                  /*pd2.providers.filters.my ++*/ pd2.providers.filters.onlyNew,
                  (dto, data) => processTrack(dto, data) *> receivedDtos.update(dto :: _)).fork

      _   <- fiber1.join *> fiber2.join
      _   <- clock.sleep(500.millis) *> progressFiber.interrupt
      res <- receivedDtos.map(_.sortBy(dto => (dto.artist, dto.title))).get

      _   <- Files.writeLines(targetPath.parent.get / Path("received.txt"), res.map(dto => s"${dto.internalId} - ${dto.artist} - ${dto.title}"))
      _   <- putStrLn(s"\ntotal tracks: ${res.length}")
    } yield ()

    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val consoleProgress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(ProgressBarDimensions(25, 65))
    val traxsourceLive = (consoleProgress ++ HttpClientZioBackend.layer()) >>> TraxsourceLive.makeLayer(8)
    val configLayer = Config.makeLayer("config.json", date1)
    val database =
      Backend.makeLayer(SQLiteProfile, Backend.makeSqliteLiveConfig(Path("c:\\!temp\\imported.db"))) >>>
      Pd2DatabaseService.makeLayer(SQLiteProfile)
    val customLayer =  traxsourceLive ++ consoleProgress ++ configLayer ++ database

    effect.provideCustomLayer(customLayer).exitCode
  }
}
