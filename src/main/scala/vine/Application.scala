package vine

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import org.fusesource.jansi.AnsiConsole
import org.slf4j.LoggerFactory
import vine.config._
import vine.conlimiter._
import vine.counters._
import vine.data._
import vine.providers.TrackDto
import vine.providers.beatport._
import vine.providers.traxsource._
import vine.ui.ProgressBarDimensions
import vine.ui.consoleprogress.ConsoleProgress.BucketRef
import vine.ui.consoleprogress._
import slick.jdbc.SQLiteProfile
import sttp.client3.httpclient.zio.SttpClient
import zio._
import zio.blocking.Blocking
import zio.console._
import zio.duration.durationInt
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.system.System
import vine.processing._
import zio.Cause._
import java.time.format.DateTimeFormatter
import java.time._
import scala.util.Try

object Application extends zio.App {

  final case class TrackMsg(dto: TrackDto, bucketRef: BucketRef)

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  {
    AnsiConsole.systemInstall()
    configureLogging

    val effect = for {
      _             <- log.info("Application starting...")
      _             <- ZIO.service[VineDatabaseImpl].flatMap(_.createSchemaIfNotExists)
      header        <- createHeader
      progressFiber <- ConsoleProgress.drawProgress(List(header, "")).repeat(Schedule.fixed(500.millis)).forever.fork
      feeds         <- Config.sourcesConfig.map(_.feeds)
      _             <- processFeeds(feeds)
      _             <- clock.sleep(1000.millis) *> progressFiber.interrupt
      _             <- Counters.ensureAllZero
    } yield ()

    makeEnvironmentOption(args) match {
      case None => printUsage.exitCode
      case Some(env) => logErrors(effect).provideCustomLayer(env)
        .catchAllCause {
          case Die(t) => putStrLn(s"Died with ${t.getMessage}")
          case Fail(t) => t match {
            case th : Throwable => putStrLn(s"Died with ${th.getMessage}")
            case e => putStrLn(s"Died with ${e.toString}")
          }
          case cause => putStrLn(cause.prettyPrint)
        }.exitCode
    }
  }

  private def createHeader: ZIO[Config, Throwable, String] = {
    for {
      (from, to) <- Config.dateFrom <*> Config.dateTo
      header     =  if (from.plusDays(1) == to) s"Period: [$from, $to)"
                    else s"Period: [$from, ${to.minusDays(1)}]"
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
      barDimensions   = ProgressBarDimensions(28, 65),
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
      VineDatabase with
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
        VineDatabaseImpl.makeLayer(SQLiteProfile)

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
    _ <- putStrLn("java -jar vine.jar --genres=house")
    _ <- putStrLn("java -jar vine.jar --genres=house --date=2021-05-01,2021-05-02")
    _ <- putStrLn("java -jar vine.jar --config=config.json --date=2021-05-01,2021-05-02")
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

  private[vine] def configureLogging : Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(context)
    context.reset()
    configurator.doConfigure(getClass.getResourceAsStream("/logback-main.xml"))
  }
}