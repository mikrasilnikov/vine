package pd2
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import org.slf4j.LoggerFactory
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
import zio.console.{Console, putStr, putStrLn}
import zio.duration.durationInt
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.system.System
import pd2.providers.filters.FilterEnv
import zio.clock.Clock
import zio.{Chunk, ExitCode, Has, Ref, Schedule, URIO, ZIO, ZLayer, clock}
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import scala.util.Try
import org.fusesource.jansi.AnsiConsole

object Application extends zio.App {

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    AnsiConsole.systemInstall()

    configureLogging()

    val periodOption = for {
      (from, to) <- getParam(args, "--date", parsePeriod, default = None)
    } yield (from, to)

    val environmentOption = for {
      (from, to)  <- periodOption
      configPath  <- getParam(args, "--config",         Path(_),     default = Some(Path("config.json")))
      dbPath      <- getParam(args, "--database",       Path(_),     default = Some(Path("data.db")))
      maxConn     <- getParam(args, "--maxConnections", _.toInt,     default = Some(16))
      download    <- getParam(args, "--downloadTracks", _.toBoolean, default = Some(true))
    } yield makeEnvironment(
      maxConnections = maxConn,
      barDimensions = ProgressBarDimensions(27, 65),
      configFilePath = configPath,
      dbFilePath  = dbPath,
      dateFrom = from,
      dateTo = to,
      downloadTracks = download)

    val effect = for {
      _             <- log.info("Application starting...")
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

    def logErrors[R,E,A](effect : ZIO[R,E,A]): ZIO[Blocking with Config with R, Any, A] = effect
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

  def printUsage : ZIO[Console, Throwable, Unit] = for {
    _ <- putStrLn("Examples:")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01,2021-05-02")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01,2021-05-02 --config=config.json")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01,2021-05-02 --config=config.json --database=data.db --maxConnections=16")
  } yield ()

  /** "2021-06-01,2021-06-02" */
  def parsePeriod(str : String) : (LocalDate, LocalDate) = {
    val splitted = str.split(',')
    if (splitted.length == 1)
      (LocalDate.parse(splitted(0)), LocalDate.parse(splitted(0)).plusDays(1))
    else
      (LocalDate.parse(splitted(0)), LocalDate.parse(splitted(1)))
  }

  /**
   * Parses command line argument
   * @param unsafeParse Unsafe conversion from string to target type. May throw exceptions.
   * @param default     Default value to return on parse error or absence of argument. Use None for required params.
   */
  def getParam[P](
    args : List[String],
    name : String,
    unsafeParse : String => P,
    default : Option[P])
  : Option[P] =
  {
    val parseResult = for {
      arg <- args.find(_.startsWith(s"$name="))
      parameterStr <- arg.split('=').lastOption
      tryParsed = Try { unsafeParse(parameterStr) }
      res <- tryParsed.fold[Option[P]](_ => None, Some(_))
    } yield res

    parseResult.orElse(default)
  }

  def processFeed(feed : Feed, refDtos : Ref[List[TrackDto]])
  : ZIO[
    Traxsource with Beatport with FilterEnv with Blocking with ConsoleProgress with Clock with Logging,
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
  : ZIO[Blocking with Config with Logging, Throwable, Unit] =
  {
    def fixPath(s : String) : String =
      s.replaceAll("([<>:\"/\\\\|?*])", "_")

    for {
      _             <- dto.uniqueNameZio.flatMap(name => log.trace(s"Processing track $name"))
      previewsBase  <- Config.previewsBasePath
      feedPath      =  previewsBase / Path(dto.feed)
      _             <- Files.createDirectory(feedPath)
                        .whenM(Files.notExists(feedPath))
      fileName      =  makeFileName(dto)
      _             <- Files.writeBytes(feedPath / Path(fixPath(fileName)), Chunk.fromArray(data))
      _             <- refDtos.update(dto :: _)
    } yield ()
  }

  def makeFileName(dto : TrackDto): String = {
    val durationStr = f"${dto.duration.toSeconds / 60}%02d:${dto.duration.toSeconds % 60}%02d"
    val withoutExt = s"[${dto.label}] [${dto.releaseName}] - ${dto.artist} - ${dto.title} - [$durationStr]"
    // Имя файла не должно быть длиннее 255 символов для windows
    (if (withoutExt.length >= 251) withoutExt.substring(0, 251) else withoutExt) ++ ".mp3"
  }

  def makeEnvironment(
    maxConnections  : Int,
    barDimensions   : ProgressBarDimensions,
    configFilePath  : Path,
    dbFilePath      : Path,
    dateFrom        : LocalDate,
    dateTo          : LocalDate,
    downloadTracks  : Boolean)
  : ZLayer[
    Console with Blocking,
    Throwable,
    Traxsource with Beatport with ConsoleProgress with Config with Has[DatabaseService] with Logging] =
  {
    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val configLayer = Config.makeLayer(configFilePath, dateFrom, dateTo, maxConnections, downloadTracks)
    val consoleProgress = (System.live ++ Console.live) >>>
      ConsoleProgressLive.makeLayer(barDimensions)

    val traxsourceLive = configLayer ++
      (consoleProgress ++ HttpClientZioBackend.layer()) >>> TraxsourceLive.makeLayer(8)

    val beatportLive = configLayer ++
      (consoleProgress ++ HttpClientZioBackend.layer()) >>> BeatportLive.makeLayer(8)

    val database =
      Backend.makeLayer(SQLiteProfile, Backend.makeSqliteLiveConfig(dbFilePath)) >>>
      DatabaseService.makeLayer(SQLiteProfile)

    val logging = Slf4jLogger.make { (context:LogContext, message) =>
      val logFormat = "[correlation-id = %s] %s"
      val correlationId = LogAnnotation.CorrelationId.render(
        context.get(LogAnnotation.CorrelationId)
      )
      //logFormat.format(correlationId, message)
      message
    }
    traxsourceLive ++ beatportLive ++ consoleProgress ++ configLayer ++ database ++ logging
  }

  def configureLogging(): Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(context)
    context.reset()
    configurator.doConfigure(getClass.getResourceAsStream("/logback-main.xml"))
  }
}
