package pd2

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import org.fusesource.jansi.AnsiConsole
import org.slf4j.LoggerFactory
import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.{ConnectionsLimiter, ConnectionsLimiterLive}
import pd2.data.{Backend, DatabaseService, Pd2Database}
import pd2.processing.Deduplication._
import pd2.processing.{Deduplication, Saving}
import pd2.providers.beatport.{Beatport, BeatportLive}
import pd2.providers.filters.{TrackFilter, getFilterByTag}
import pd2.providers.traxsource.{Traxsource, TraxsourceLive}
import pd2.providers.{TrackDto, filters, getProviderByFeedTag}
import pd2.ui.ProgressBarDimensions
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
import pd2.ui.consoleprogress.{ConsoleProgress, ConsoleProgressLive}
import slick.jdbc.SQLiteProfile
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.Uri
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio.nio.core.file.Path
import zio.nio.file.Files
import zio.system.System

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import scala.util.Try

object Application extends zio.App {

  final case class TrackMsg(dto: TrackDto, bucketRef: BucketRef)

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  {
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
      hostConnections = 8,
      maxConnections  = maxConn,
      barDimensions   = ProgressBarDimensions(27, 65),
      configFilePath  = configPath,
      dbFilePath      = dbPath,
      dateFrom        = from,
      dateTo          = to,
      downloadTracks  = download)

    val effect = for {
      _             <- log.info("Application starting...")
      previewsBase  <- Config.previewsBasePath
      _             <- Files.createDirectory(previewsBase).whenM(Files.notExists(previewsBase))

      progressFiber <- ConsoleProgress.drawProgress.repeat(Schedule.fixed(500.millis)).forever.fork

      feeds         <- Config.configDescription.map(_.feeds)
      _             <- processFeeds(feeds)

      _             <- clock.sleep(1000.millis) *> progressFiber.interrupt
    } yield ()

    environmentOption match {
      case None => printUsage.exitCode
      case Some(env) => logErrors(effect).provideCustomLayer(env)
        .catchAll(e => putStrLn(e.toString)).exitCode
    }
  }

  private def processFeeds(feeds : List[Feed]) =
  {
    val feedsByPriority = feeds.groupBy(_.priority).toList.sortBy { case (p, _) => p }
    val sortedFeeds = feedsByPriority.flatMap { case (_, group) => group }

    for {
      _ <- ZIO.foreach_(sortedFeeds)(f => ConsoleProgress.initializeBar(f.name, List(1)))
      _ <- ZIO.foreach_(feedsByPriority) { case (_, group) => ZIO.foreachPar_(group)(processFeed) }
      } yield ()
  }

  def processFeed(feed : Feed) = {
    for {
      folderSem   <- Semaphore.make(1)
      feedPath    <- Config.previewsBasePath.map(_ / Path(feed.name))

      (from, to)  <- Config.dateFrom <*> Config.dateTo
      workerNum   <- Config.connectionsPerHost
      provider    <- getProviderByFeedTag(feed.tag)
      queue       <- Queue.bounded[TrackMsg](1000)
      filter      =  feed.filterTags.map(getFilterByTag).foldLeft(filters.withArtistAndTitle)(_ ++ _)

      workersFib  <- ZIO.forkAll(List.fill(workerNum)(worker(queue, filter, feedPath, folderSem)))
      _           <- provider.processTracks(feed, from, to, queue)
      _           <- workersFib.join.foldCause(_ => (), _ => ())
    } yield ()
  }

  def worker(
    queue : Queue[TrackMsg],
    filter : TrackFilter,
    targetPath : Path,
    folderSem : Semaphore) =
  {
    def deduplicateOrDownload(msg : TrackMsg) = for
    {
      dResult <- Deduplication.deduplicateOrEnqueue(msg.dto)

      download=  ZIO.effect(Uri.unsafeParse(msg.dto.mp3Url))
                  .flatMap(uri => Saving.downloadWithRetry(uri, targetPath, folderSem)
                    .whenM(Config.downloadTracks))
      _       <- dResult match {
                    case Duplicate(_)   => ZIO.succeed()
                    case InProcess(_)   => ZIO.succeed()
                    case Enqueued(_)    => download
                    case Resumed(_, _)  => download
                  }
      _       <- Deduplication.markAsCompleted(dResult)
    } yield ()

    val process1 = for {
      msg <- queue.take
      _ <- deduplicateOrDownload(msg).whenM(filter.check(msg.dto))
            .foldCauseM( // Report error to user, continue processing
              c =>  log.error(s"Download failed\nUrl: ${msg.dto.mp3Url}\n${c.prettyPrint}") *>
                    ConsoleProgress.failOne(msg.bucketRef),
              _ =>  ConsoleProgress.completeOne(msg.bucketRef))
    } yield ()

    process1.forever
  }

  def logErrors[R,E,A](effect : ZIO[R,E,A]): ZIO[Blocking with Config with R, Any, A] = effect
    .tapCause(e => for {
      now       <- ZIO.effectTotal(LocalDateTime.now())
      appPath   <- Config.appPath
      fileName  =  s"error-${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}.txt"
        .replaceAll(":", "")
      _         <- Files.writeLines(appPath / Path(fileName), List(e.prettyPrint))
    } yield ())

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

  def makeFileName(dto : TrackDto): String = {
    val durationStr = f"${dto.duration.toSeconds / 60}%02d:${dto.duration.toSeconds % 60}%02d"
    val withoutExt = s"[${dto.label}] [${dto.releaseName}] - ${dto.artist} - ${dto.title} - [$durationStr]"
    // Имя файла не должно быть длиннее 255 символов для windows
    (if (withoutExt.length >= 251) withoutExt.substring(0, 251) else withoutExt) ++ ".mp3"
  }

  def makeEnvironment(
    hostConnections : Int,
    maxConnections  : Int,
    barDimensions   : ProgressBarDimensions,
    configFilePath  : Path,
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
      SttpClient] =
  {
    import sttp.client3.httpclient.zio.HttpClientZioBackend

    val config = Config.makeLayer(configFilePath, dateFrom, dateTo, hostConnections, maxConnections, downloadTracks)
    val conLimiter = ConnectionsLimiterLive.makeLayer(maxConnections, hostConnections)
    val progress = (System.live ++ Console.live) >>> ConsoleProgressLive.makeLayer(barDimensions)
    val sttpClient = HttpClientZioBackend.layer()

    val traxsource  = config ++ progress ++ sttpClient >>> TraxsourceLive.makeLayer
    val beatport    = config ++ progress ++ sttpClient >>> BeatportLive.makeLayer

    val database =
      Backend.makeLayer(SQLiteProfile, Backend.makeSqliteLiveConfig(dbFilePath)) >>>
      DatabaseService.makeLayer(SQLiteProfile)

    val logging = Slf4jLogger.make ((_, message) => message)

    traxsource ++ beatport ++ progress ++ config ++
      database ++ logging ++ conLimiter ++ sttpClient
  }

  def configureLogging(): Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()
    configurator.setContext(context)
    context.reset()
    configurator.doConfigure(getClass.getResourceAsStream("/logback-main.xml"))
  }
}
