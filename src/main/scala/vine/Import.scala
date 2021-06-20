package vine

import org.fusesource.jansi.AnsiConsole
import vine.data.TrackParsing._
import vine.data._
import vine.data.TrackParsing
import vine.helpers.Conversions.OptionToZio
import vine.ui.ProgressBarDimensions
import vine.ui.consoleprogress._
import slick.jdbc.SQLiteProfile
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.nio.core.file._
import zio.nio.file._
import zio.stream._
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.collection.immutable.HashSet
import scala.collection.{immutable, mutable}

object Import extends zio.App {

  case class Params(sourcePath : Path, outputPath : Path)

  private def printUsage = for {
    _ <- putStrLn("Example: java -cp vine.jar vine.Import imported.db c:\\vine\\data\\tracks")
  } yield ()

  private def parseAndValidateParams(args: List[String]) = {
    for {
      _ <- ZIO.fail(new IllegalArgumentException("Expecting two arguments")).unless(args.length == 2)
      outputPath = Path(args(0))
      sourcePath = Path(args(1))
      _ <- ZIO.fail(new IOException(s"File $outputPath already exists"))    .whenM  (Files.exists(outputPath))
      _ <- ZIO.fail(new IOException(s"Path $sourcePath does not exist"))    .unlessM(Files.exists(sourcePath))
      _ <- ZIO.fail(new IOException(s"Path $sourcePath is not a directory")).unlessM(Files.isDirectory(sourcePath))
    } yield Params(sourcePath, outputPath)
  }

  def run(args: List[String]) = {

    AnsiConsole.systemInstall()
    vine.Application.configureLogging

    def customLayer(params : Params) = {

      val databaseService =
        Backend.makeLayer(SQLiteProfile, Backend.makeSqliteLiveConfig(params.outputPath)) >>>
        VineDatabaseImpl.makeLayer(SQLiteProfile)

      val consoleProgress =
        (system.System.live ++ Console.live) >>>
          ConsoleProgressLive.makeLayer(ProgressBarDimensions(25, 60))

      databaseService ++ consoleProgress
    }

    val app = parseAndValidateParams(args)
      .foldM (
        e => putStrLn(e.getMessage + "\n") *> printUsage,
        params => performImport(params).provideCustomLayer(customLayer(params)))

    app.exitCode
  }

  private def performImport(params : Params)
    : ZIO[Console with VineDatabase with ConsoleProgress with Blocking with Clock, Throwable, Unit] =
  {
    val batchSize = 100

    ZIO.service[VineDatabaseImpl].flatMap { db =>

      import db.profile.api._

      for {
        _ <- putStrLn(s"Creating schema...")
        _ <- db.run(db.tracks.schema.create)

        files <- Files.list(params.sourcePath)
          .filter(path => path.filename.toString.endsWith(".txt"))
          .runCollect

        progressBucket<- ConsoleProgress.initializeBar("Processing files", List(files.length)).map(_.head)
        progressFiber <- ConsoleProgress.drawProgress.repeat(Schedule.duration(333.millis)).forever.fork

        hashRef   <- Ref.make[HashSet[String]](HashSet[String]())
        _         <- ZIO.foreach_(files) { filePath =>
                      uniqueTracksStream(filePath, hashRef)
                        .grouped(batchSize)
                        .foreach { track =>
                          db.run((db.tracks ++= track).transactionally)
                      } *> ConsoleProgress.completeOne(progressBucket)
                    }
        _         <- clock.sleep(667.millis) *> progressFiber.interrupt
      } yield ()
    }
  }

  /**
   * Builds a stream of unique tracks from a text file (v1 csv format).
   * @param hashRef a reference to a hash set which is used for keeping track of duplicate unique names.
   * */
  private def uniqueTracksStream(
    filePath : Path,
    hashRef : Ref[immutable.HashSet[String]])
  : ZStream[Blocking, Throwable, Track] =
  {
    ZStream.fromIterableM(Files.readAllLines(filePath, charset = StandardCharsets.UTF_8))
      .mapM(line => parseLine(line).toZio.orElseFail(new Exception(s"Could not parse line $line")))
      .filterM(track =>
        hashRef.modify(map =>
          if (map.contains(track.uniqueName)) (false, map)
          else (true, map + track.uniqueName))
      )
  }

  private def parseLine(line : String) : Option[Track] =
  {
    val parts = line.split('\t')

    val artist = parts(0)
    val title = parts(1)

    val label = if (parts.length == 5) Some(parts(2)) else None

    val releaseDate =
      if (parts.length == 5)
        Some(LocalDate.parse(parts(3), DateTimeFormatter.ofPattern("dd.MM.uuuu")))
      else None

    val feed = if (parts.length == 5) Some(parts(4)) else None

    for {
      parsedArtists <- TrackParsing.parseArtists(artist)
      parsedTitle <- TrackParsing.parseTitle(title)
      rewritten = rewriteTrackName(parsedArtists, parsedTitle)
    } yield Track(artist, title, rewritten, label, releaseDate, feed, None)
  }
}
