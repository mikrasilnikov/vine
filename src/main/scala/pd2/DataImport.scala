package pd2

import com.typesafe.config._
import pd2.data.TrackParsing._
import pd2.data.TrackTable.Track
import pd2.data.{TrackParsing, TrackRepository}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import zio._
import zio.blocking.Blocking
import zio.console.putStrLn
import zio.nio.core.file._
import zio.nio.file._
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time._
import java.time.format.DateTimeFormatter

object DataImport extends zio.App {

  val dataPath = "c:\\Music-Sly\\PreviewsDownloader\\data\\tracks\\"

  def run(args: List[String]) = {
    performImport(dataPath)
      .provideCustomLayer(createCustomLayer(Path("c:\\!temp\\tracks.db")))
      .exitCode
  }

  private def createCustomLayer(targetFilePath : Path) = {

    val configMap = new java.util.HashMap[String, String]
    configMap.put("driver", "org.sqlite.JDBC")
    configMap.put("url", s"jdbc:sqlite:${targetFilePath.toString}")
    configMap.put("connectionPool", "disabled")

    val config = ConfigFactory.parseMap(configMap)

    val dbProfile = ZLayer.succeed(slick.jdbc.SQLiteProfile.asInstanceOf[JdbcProfile])
    val dbConfig = ZLayer.fromEffect (ZIO.effect(config))

    (dbConfig ++ dbProfile) >>> DatabaseProvider.live >>> TrackRepository.live
  }

  private def performImport(dataPath : String) = {
    for {
      tracks <- getUniqueTracks(dataPath)
      _ <- putStrLn(s"uniqueTracks: ${ tracks.length }")
      _ <- TrackRepository.createSchema
      _ <- ZIO.foreach_(tracks.grouped(10).toList)(TrackRepository.insertSeq)
    } yield ()
  }

  private def getUniqueTracks(dataPath : String): ZIO[Blocking, IOException, List[Track]] = {
     for {
      lines <- Files.list(Path(dataPath))
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

  private def rewriteTrackName(parsedArtists : List[Artist], parsedTitle : Title) : String = {

    def getAllNames(artist : Artist) : List[String] = artist match {
      case Single(name) => List(name)
      case Feat(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
      case Coop(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
      case Pres(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
    }

    val artistNamesFromArtistFiled = parsedArtists.flatMap(a => getAllNames(a))

    val featuredArtistNamesO = for {
      aList <- parsedTitle.featuredArtist
    } yield aList.flatMap(a => getAllNames(a))

    val artistNames =
      (artistNamesFromArtistFiled ++ featuredArtistNamesO.orElse(Some(List[String]())).get)
        .distinct
        .sorted

    s"${artistNames.mkString(", ")} - ${parsedTitle.actualTitle}${parsedTitle.mix.map(" " + _).getOrElse("")}"
  }

}
