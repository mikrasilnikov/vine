package pd2

import java.nio.file.{Files, OpenOption, Path, StandardOpenOption}
import scala.jdk.StreamConverters.StreamHasToScala
import pd2.data.{TrackParsing, TrackRepository, TrackTable}
import pd2.data.TrackTable.Track
import pd2.data.TrackParsing._

import java.time._
import java.time.format.DateTimeFormatter
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import scala.collection.parallel.CollectionConverters._
import slick.jdbc.{JdbcProfile, SQLiteProfile}
import slick.jdbc.SQLiteProfile.api._
import zio._
import slick.interop.zio.syntax._
import slick.interop.zio.DatabaseProvider
import com.typesafe.config._
import scala.concurrent.ExecutionContext.Implicits.global

object DataImport {

  case class NewRow(artist: String, title: String, label: String, releaseDate: String, feedName: String)

  def rewriteTrackName(parsedArtists : List[Artist], parsedTitle : Title) : String = {

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

  def main(args: Array[String]): Unit = {

    val dataPath = "c:\\Music-Sly\\PreviewsDownloader\\data\\tracks\\"

    val parsedData = Files.list(Path.of(dataPath))
      .flatMap(path => Files.lines(path, StandardCharsets.UTF_8))
      .toScala(LazyList)
      //.take(1000)
      .par
      .map(line => line.split('\t'))
      .map { parts =>
        val artist = parts(0)
        val title = parts(1)
        val label =       if (parts.length == 5) Some(parts(2)) else None
        val releaseDate = if (parts.length == 5)
          Some(LocalDate.parse(parts(3), DateTimeFormatter.ofPattern("dd.MM.uuuu")))
          else None
        val feed =    if (parts.length == 5) Some(parts(4)) else None

        val rewritten = for {
          artists <- TrackParsing.parseArtists(artist)
          title <- TrackParsing.parseTitle(title)
        } yield rewriteTrackName(artists, title)

        if (rewritten.isEmpty)
          throw new Exception(s"could not parse ${parts.mkString("Array(", ", ", ")")}")

        Track(artist, title, rewritten.get, label, releaseDate, feed)
      }

    val uniqueTracks = parsedData
      .groupBy(_.uniqueName)
      .map { case (_, items) => items.head }
      .toList

    println(s"uniqueTracks: ${uniqueTracks.length}")

    val dbio = for {
      _ <- TrackTable.table.schema.create
      _ <- DBIO.sequence(uniqueTracks.grouped(1000).map(part => TrackTable.table ++= part))
      //_ <- TrackTable.table ++= uniqueTracks
    } yield ()

    val zio = ZIO.fromDBIO(dbio)

    val configMap = new java.util.HashMap[String, String]
    configMap.put("driver", "org.sqlite.JDBC")
    configMap.put("url", "jdbc:sqlite:c:\\!temp\\testdb.db")
    configMap.put("connectionPool", "disabled")

    val config = ConfigFactory.parseMap(configMap)

    val dbProfile = ZLayer.succeed(slick.jdbc.SQLiteProfile.asInstanceOf[JdbcProfile])
    val dbConfig = ZLayer.fromEffect (ZIO.effect(config))

    val customLayer = (dbConfig ++ dbProfile) >>> DatabaseProvider.live

    Runtime.default.unsafeRun(zio.provideCustomLayer(customLayer))


    /*val pw = new PrintWriter(new FileWriter("d:\\!temp\\rewritten.txt"))
    pw.println()
    pw.close()*/

  }
}
