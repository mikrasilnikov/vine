package pd2

import com.typesafe.config._
import pd2.data.TrackParsing._
import pd2.data.TrackRepository.TrackRepository
import pd2.data.TrackTable.Track
import pd2.data.{TrackParsing, TrackRepository}
import pd2.ui.ConsoleProgressService.ConsoleProgress
import pd2.ui.ProgressBar.{InProgress, ProgressBarDimensions, ProgressBarLayout}
import pd2.ui.{ConsoleASCII, ConsoleProgressLive, ConsoleProgressService => Progress}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.nio.core.file._
import zio.nio.file._

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DataImport extends zio.App {

  case class Params(sourcePath : Path, outputPath : Path)

  private def printUsage = for {
    _ <- putStrLn("Usage:   java -cp pd2.DataImport {OutputFile} {SourceDataPath}")
    _ <- putStrLn("Example: java -cp pd2.DataImport c:\\PreviewsDownloader2\\imported.db c:\\PreviwsDownloader\\data\\tracks")
  } yield ()

  private def parseAndValidateParams(args: List[String]) = {
    for {
      _ <- ZIO.fail(new IllegalArgumentException("Expecting two arguments")).unless(args.length == 2)
      outputPath = Path(args(0))
      sourcePath = Path(args(1))
      outputParent  <- ZIO.fromOption(outputPath.parent).orElseFail(new IOException("outputPath.parent is empty"))
      _ <- ZIO.fail(new IOException(s"File $outputPath already exists"))       .whenM  (Files.exists(outputPath))
      _ <- ZIO.fail(new IOException(s"Path $outputParent does not exist"))     .unlessM(Files.exists(outputParent))
      _ <- ZIO.fail(new IOException(s"Path $outputParent is not a directory")) .unlessM(Files.isDirectory(outputParent))
      _ <- ZIO.fail(new IOException(s"Path $sourcePath does not exist"))       .unlessM(Files.exists(sourcePath))
      _ <- ZIO.fail(new IOException(s"Path $sourcePath is not a directory"))   .unlessM(Files.isDirectory(sourcePath))
    } yield Params(sourcePath, outputPath)
  }

  def run(args: List[String]) = {

    def customLayer(params : Params) =
      (createDbLayer(params.outputPath) >>> TrackRepository.live) ++
      ConsoleProgressLive.make(ProgressBarDimensions(15, 60))

    val app = parseAndValidateParams(args)
      .foldM (
        e => putStrLn(ConsoleASCII.Color.red + e.getMessage + ConsoleASCII.Color.white) *> putStrLn("") *> printUsage,
        params => performImport(params).provideCustomLayer(customLayer(params)))

    app.exitCode
  }

  private def createDbLayer(targetFilePath : Path): ZLayer[Any, Throwable, Has[DatabaseProvider]] =
  {
    val configMap = new java.util.HashMap[String, String]
    configMap.put("driver", "org.sqlite.JDBC")
    configMap.put("url", s"jdbc:sqlite:${targetFilePath.toString}")
    configMap.put("connectionPool", "disabled")

    val config = ConfigFactory.parseMap(configMap)

    val dbProfile = ZLayer.succeed(slick.jdbc.SQLiteProfile.asInstanceOf[JdbcProfile])
    val dbConfig = ZLayer.fromEffect (ZIO.effect(config))

    (dbConfig ++ dbProfile) >>> DatabaseProvider.live
  }

  private def performImport(params : Params)
    : ZIO[Console with TrackRepository with ConsoleProgress with Blocking with Clock, Throwable, Unit] =
  {
    val batchSize = 100
    for {
      _ <- putStrLn("Parsing source data into memory...")
      tracks <- getUniqueTracks(params.sourcePath)
      _ <- putStrLn(s"Got ${tracks.length} unique tracks")
      _ <- putStrLn(s"Creating schema...")
      _ <- TrackRepository.createSchema
      _ <- putStrLn(s"Inserting rows to database...")

      prgItems  <- Progress.acquireProgressItems("Importing data", tracks.length / batchSize)
      _         <- Progress.drawProgress.repeat(Schedule.duration(333.millis)).forever.fork
      _         <- ZIO.foreach_(tracks.grouped(batchSize).zip(prgItems).toList) {
                  case (batch, pItem) =>
                    Progress.updateProgressItem(pItem, InProgress) *>
                    TrackRepository.insertSeq(batch) *>
                    Progress.completeProgressItem(pItem)
                }
    } yield ()
  }

  private def getUniqueTracks(dataPath : Path): ZIO[Blocking, IOException, List[Track]] = {
     for {
      lines <- Files.list(dataPath)
        .mapM(p => Files.readAllLines(p, StandardCharsets.UTF_8))
        .runCollect
        .map(chunk => chunk.toList.flatten)
      parsed <- ZIO.foreachPar(lines)(line => ZIO.succeed(parseLine(line)))
      unique = parsed
        .groupBy(_.uniqueName)
        .map { case (_, items) => items.minBy(t => t.artist.length + t.title.length) }
    } yield unique.toList
  }

  private def parseLine(line : String) : Track = {
    val parts = line.split('\t')

    val artist = parts(0)
    val title = parts(1)

    val label = if (parts.length == 5) Some(parts(2)) else None

    val releaseDate =
      if (parts.length == 5)
        Some(LocalDate.parse(parts(3), DateTimeFormatter.ofPattern("dd.MM.uuuu")))
      else None

    val feed = if (parts.length == 5) Some(parts(4)) else None

    val rewritten = for {
      artists <- TrackParsing.parseArtists(artist)
      title <- TrackParsing.parseTitle(title)
    } yield rewriteTrackName(artists, title)

    if (rewritten.isDefined)
      Track(artist, title, rewritten.get, label, releaseDate, feed)
    else
      Track(artist, title, s"$artist - $title", None, None, None)
  }

}
