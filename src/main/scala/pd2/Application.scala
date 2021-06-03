package pd2
import pd2.config.Config
import pd2.config.ConfigDescription.{Feed, FeedTag, FilterTag}
import pd2.data.{Backend, DatabaseService}
import pd2.providers.{TrackDto, filters, getProviderByFeedTag}
import pd2.providers.beatport.{Beatport, BeatportLive}
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
import pd2.providers.filters.FilterEnv
import zio.clock.Clock
import zio.{Chunk, ExitCode, Has, Ref, Schedule, URIO, ZIO, ZLayer, clock}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import scala.util.Try

object Application extends zio.App {

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val explicitPeriodOption = for {
      dateFrom  <- getParamOption(args, "--dateFrom", LocalDate.parse)
      dateTo    <- getParamOption(args, "--dateTo", LocalDate.parse)
    } yield (dateFrom, dateTo)

    val singleDayPeriodOption = for {
      date <- getParamOption(args, "--date", LocalDate.parse)
    } yield (date, date.plusDays(1))

    val environmentOption = for {
      (from, to)  <- explicitPeriodOption.orElse(singleDayPeriodOption)
      configPath  <- getParamOption(args, "--config", Path.apply(_)).orElse(Some(Path("config.json")))
      dbPath      <- getParamOption(args, "--database", Path.apply(_)).orElse(Some(Path("data.db")))
      maxConn     <- getParamOption(args, "--maxConnections", _.toInt).orElse(Some(16))
    } yield makeEnvironment(
      maxConnections = maxConn,
      barDimensions = ProgressBarDimensions(30, 65),
      configFilePath = configPath,
      dbFilePath  = dbPath,
      dateFrom = from,
      dateTo = to)

    val effect = for {
      previewsBase  <- Config.previewsBasePath
      _             <- Files.createDirectory(previewsBase).whenM(Files.notExists(previewsBase))

      progressFiber <- ConsoleProgress.drawProgress.repeat(Schedule.fixed(500.millis)).forever.fork
      processedRef  <- Ref.make(List[TrackDto]())

      feeds         <- Config.configDescription.map(_.feeds)

      feedsByPriority= feeds.groupBy(_.priority).toList.sortBy { case (p, _) => p }

      // Сначала регистрируем фиды, остортированные по приоритету в ConsoleProgress (запрашивая 0 Item-ов для каждого)
      sortedFeeds    = feedsByPriority.flatMap { case (_, value) => value }
      _             <- ZIO.foreach_(sortedFeeds)(f => ConsoleProgress.acquireProgressItems(f.name, 0))

      // Обрабатываем фиды по приоритетам
      _             <- ZIO.foreach_(feedsByPriority) { case (_, items) =>
                        ZIO.foreachPar_(items)(f => processFeed(f, processedRef).tapError(e => putStrLn(e.toString)))
                      }

      _             <- clock.sleep(500.millis) *> progressFiber.interrupt
      processed     <- processedRef.get
      _             <- putStrLn(s"\ntotal tracks: ${processed.length}")
    } yield ()

    def logErrors[R,E,A](effect : ZIO[R,E,A]) = effect
      .tapCause(e => for {
        now       <- ZIO.effectTotal(LocalDateTime.now())
        appPath   <- Config.appPath
        fileName  =  s"error-${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.txt"
                      .replaceAll(":", "")
        _         <- Files.writeLines(appPath / Path(fileName), List(e.prettyPrint))
      } yield ())

    environmentOption match {
      case None => printUsage.exitCode
      case Some(env) => logErrors(effect).provideCustomLayer(env)
        .catchAll(e => putStrLn(e.toString)).exitCode
    }
  }

  def printUsage : ZIO[Console, Nothing, Unit] = for {
    _ <- putStrLn("Examples:")
    _ <- putStrLn("java -cp PreviewsDownloader2.jar pd2.Application --date=2021-05-01")
    _ <- putStrLn("java -cp PreviewsDownloader2.jar pd2.Application --dateFrom=2021-05-01 --dateTo=2021-05-07")
    _ <- putStrLn("java -cp PreviewsDownloader2.jar pd2.Application --dateFrom=2021-05-01 --dateTo=2021-05-07 --config=config.json")
    _ <- putStrLn("java -cp PreviewsDownloader2.jar pd2.Application --dateFrom=2021-05-01 --dateTo=2021-05-07 --config=config.json --database=data.db --maxConnections=16")
  } yield ()

  def getParamOption[P](args : List[String], name : String, unsafeParse : String => P) : Option[P] =
    for {
      arg <- args.find(_.startsWith(s"$name="))
      parameterStr <- arg.split('=').lastOption
      tryParsed = Try { unsafeParse(parameterStr) }
      res <- tryParsed.fold[Option[P]](_ => None, Some(_))
    } yield res

  def processFeed(feed : Feed, refDtos : Ref[List[TrackDto]])
  : ZIO[
    Traxsource with Beatport with FilterEnv with Blocking with ConsoleProgress with Clock,
    Throwable, Unit] =
    for {
      feedFilter  <- ZIO.succeed(feed.filterTags
                      .map(filters.getFilterByTag)
                      .foldLeft(filters.withArtistAndTitle)(_ ++ _))
      dateFrom    <- Config.dateFrom
      dateTo      <- Config.dateTo
      musicStore  <- getProviderByFeedTag(feed.tag)
      _           <- musicStore.processTracks(feed, dateFrom, dateTo, feedFilter,
                      (dto, data) => processTrack(dto, data, refDtos))
    } yield ()

  def processTrack(dto: TrackDto, data: Array[Byte], refDtos : Ref[List[TrackDto]])
  : ZIO[Blocking with Config, Throwable, Unit] =
  {
    def fixPath(s : String) : String =
      s.replaceAll("([<>:\"/\\\\|?*])", "_")

    for {
      previewsBase  <- Config.previewsBasePath
      feedPath      =  previewsBase / Path(dto.feed)
      _             <- Files.createDirectory(feedPath).whenM(Files.notExists(feedPath))
      fileName      =  makeFileName(dto)
      _             <- Files.writeBytes(feedPath / Path(fixPath(fileName)), Chunk.fromArray(data))
      _             <- refDtos.update(dto :: _)
    } yield ()
  }

  def makeFileName(dto : TrackDto): String = {
    val durationStr = f"${dto.duration.toSeconds / 60}%02d:${dto.duration.toSeconds % 60}%02d"
    val withoutExt = s"[${dto.label}] [${dto.releaseName}] - ${dto.artist} - ${dto.title} - [$durationStr]"
    // Имя файла не должно быть длиннее 255 символов для windows
    withoutExt.substring(0, 251) ++ ".mp3"
  }

  def makeEnvironment(
    maxConnections  : Int,
    barDimensions   : ProgressBarDimensions,
    configFilePath  : Path,
    dbFilePath      : Path,
    dateFrom        : LocalDate,
    dateTo          : LocalDate)
  : ZLayer[
    Console with Blocking,
    Throwable,
    Traxsource with Beatport with ConsoleProgress with Config with Has[DatabaseService]] =
  {
    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val configLayer = Config.makeLayer(configFilePath, dateFrom, dateTo, maxConnections)
    val consoleProgress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(barDimensions)

    val traxsourceLive = configLayer ++
      (consoleProgress ++ HttpClientZioBackend.layer()) >>> TraxsourceLive.makeLayer(8)

    val beatportLive = configLayer ++
      (consoleProgress ++ HttpClientZioBackend.layer()) >>> BeatportLive.makeLayer(8)

    val database =
      Backend.makeLayer(SQLiteProfile, Backend.makeSqliteLiveConfig(dbFilePath)) >>>
      DatabaseService.makeLayer(SQLiteProfile)

    traxsourceLive ++ beatportLive ++ consoleProgress ++ configLayer ++ database
  }
}
