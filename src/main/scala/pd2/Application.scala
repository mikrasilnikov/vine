package pd2

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import org.fusesource.jansi.AnsiConsole
import org.slf4j.LoggerFactory
import pd2.config._
import pd2.conlimiter.{ConnectionsLimiter, ConnectionsLimiterLive}
import pd2.counters.{Counters, CountersLive}
import pd2.data.{Backend, DatabaseService, Pd2Database}
import pd2.providers.TrackDto
import pd2.providers.beatport.{Beatport, BeatportLive}
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.ui.ProgressBarDimensions
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import slick.jdbc.SQLiteProfile
import sttp.client3.httpclient.zio.SttpClient
import zio._
import zio.blocking.Blocking
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.system.System
import pd2.processing._
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import scala.util.Try

object Application extends zio.App {

  final case class TrackMsg(dto: TrackDto, bucketRef: BucketRef)

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  {
    AnsiConsole.systemInstall()
    configureLogging

    val effect = for {
      _             <- log.info("Application starting...")

      header        <- createHeader
      progressFiber <- ConsoleProgress.drawProgress(List(header)).repeat(Schedule.fixed(500.millis)).forever.fork
      feeds         <- Config.sourcesConfig.map(_.feeds)
      _             <- processFeeds(feeds)
      _             <- clock.sleep(1000.millis) *> progressFiber.interrupt
      _             <- Counters.ensureAllZero
    } yield ()

    makeEnvironmentOption(args) match {
      case None => printUsage.exitCode
      case Some(env) => logErrors(effect).provideCustomLayer(env)
        .catchAll(e => putStrLn(e.toString)).exitCode
    }
  }

  private def createHeader: ZIO[Config, Throwable, String] = {
    for {
      (from, to) <- Config.dateFrom <*> Config.dateTo
      header     =  if (from.plusDays(1) == to) s"\tDate: $from"
                    else " " * 27 + s"Dates: [$from, $to]"
    } yield header
  }

  private def makeEnvironmentOption(args : List[String]) =
  {
    // By default fetching tracks from the day before yesterday.
    val periodOption = for {
      (from, to) <- getParam(args, "--date", parsePeriod,
        default = Some((
          LocalDate.now().minusDays(3),
          LocalDate.now().minusDays(2))))
    } yield (from, to)

    val sourcesConfigOption : Option[SourcesMode] =
      getParam(args, "--genres", _.split(',').toList).map(FromGenreNames).orElse(
      getParam(args, "--config", Path(_), default = Some(Path("config.json"))).map(FromConfigFile))

    for {
      (from, to)  <- periodOption
      srcConfig   <- sourcesConfigOption
      dbPath      <- getParam(args, "--database",       Path(_),     default = Some(Path("data.db")))
      maxConn     <- getParam(args, "--maxConnections", _.toInt,     default = Some(16))
      download    <- getParam(args, "--downloadTracks", _.toBoolean, default = Some(true))
    } yield makeEnvironment(
      hostConnections = 8,
      maxConnections  = maxConn,
      barDimensions   = ProgressBarDimensions(27, 65),
      srcConfig       = srcConfig,
      dbFilePath      = dbPath,
      dateFrom        = from,
      dateTo          = to,
      downloadTracks  = download)
  }

  private def makeEnvironment(
    hostConnections : Int,
    maxConnections  : Int,
    barDimensions   : ProgressBarDimensions,
    srcConfig       : SourcesMode,
    dbFilePath      : Path,
    dateFrom        : LocalDate,
    dateTo          : LocalDate,
    downloadTracks  : Boolean)
  : ZLayer[
    Console with Blocking, Throwable,
    Traxsource with
      Beatport with
      ConsoleProgress with
      Config with
      Pd2Database with
      Logging with
      ConnectionsLimiter with
      SttpClient with
      Counters] =
  {
    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val config = Config.makeLayer(srcConfig, dateFrom, dateTo, hostConnections, maxConnections, downloadTracks)
    val conLimiter = ConnectionsLimiterLive.makeLayer(maxConnections, hostConnections)
    val progress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(barDimensions)
    val sttpClient = HttpClientZioBackend.layer()

    val traxsource  = config ++ progress ++ sttpClient >>> TraxsourceLive.makeLayer
    val beatport    = config ++ progress ++ sttpClient >>> BeatportLive.makeLayer

    val database =
      Backend.makeLayer(SQLiteProfile, Backend.makeSqliteLiveConfig(dbFilePath)) >>>
        DatabaseService.makeLayer(SQLiteProfile)

    val logging = Slf4jLogger.make ((_, message) => message)

    val counters = CountersLive.makeLayer

    traxsource ++ beatport ++ progress ++ config ++
      database ++ logging ++ conLimiter ++ sttpClient ++ counters
  }
  
  private def logErrors[R,E,A](effect : ZIO[R,E,A]): ZIO[Blocking with Config with R, Any, A] = effect
    .tapCause(e => for {
      now       <- ZIO.effectTotal(LocalDateTime.now())
      appPath   <- Config.appPath
      fileName  =  s"error-${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.txt"
                    .replaceAll(":", "")
      _         <- Files.writeLines(appPath / Path(fileName), List(e.prettyPrint))
    } yield ())

  private def printUsage : ZIO[Console, Throwable, Unit] = for {
    _ <- putStrLn("Examples:")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01,2021-05-02")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01,2021-05-02 --config=config.json")
    _ <- putStrLn("java -jar PreviewsDownloader2.jar --date=2021-05-01,2021-05-02 --config=config.json --database=data.db --maxConnections=16")
  } yield ()

  /** "2021-06-01,2021-06-03" */
  private def parsePeriod(str : String) : (LocalDate, LocalDate) = {
    val splitted = str.split(',')
    if (splitted.length == 1)
      (LocalDate.parse(splitted(0)), LocalDate.parse(splitted(0)).plusDays(1))
    else
      (LocalDate.parse(splitted(0)), LocalDate.parse(splitted(1)).plusDays(1))
  }

  /**
   * Parses command line argument
   * @param unsafeParse Unsafe conversion from string to target type. May throw exceptions.
   * @param default     Default value to return on parse error or absence of argument. Use None for required params.
   */
  private def getParam[P](
    args : List[String],
    name : String,
    unsafeParse : String => P,
    default : Option[P] = None)
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

  private[pd2] def configureLogging : Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(context)
    context.reset()
    configurator.doConfigure(getClass.getResourceAsStream("/logback-main.xml"))
  }
}