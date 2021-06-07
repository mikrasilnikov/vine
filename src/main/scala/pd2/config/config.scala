package pd2
import zio.blocking.Blocking
import zio.{Has, Semaphore, Task, ZIO, ZLayer}
import zio.macros.accessible
import zio.nio.core.file.Path
import zio.nio.file.Files
import io.circe.parser._
import pd2.config.ConfigDescription.{Feed, FeedTag, FilterTag}
import pd2.helpers.Conversions.EitherToZio
import zio.console.{Console, putStr, putStrLn}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files => JFiles, Path => JPath}
import java.time.{LocalDate, LocalDateTime}
import scala.util.matching.Regex

package object config {

  type Config = Has[Config.Service]

  @accessible
  object Config {

    trait Service {
      def configDescription   : ConfigDescription
      def dateFrom            : LocalDate
      def dateTo              : LocalDate
      /** Regular expressions to match on track artist and title. Used by "my" filter. */
      def myArtistsRegexes    : List[Regex]
      def myLabels            : List[String]
      def shitLabels          : List[String]
      def previewsBasePath    : Path
      def runId               : LocalDateTime
      def globalConnSemaphore : Semaphore
      def appPath             : Path
      def downloadTracks      : Boolean
    }

    /**
     * @param filePath относительный путь к файлу конфигурации
     * @param from стартовая дата релиза треков (включительно)
     * @param to конечная дата релиза треков (не включительно)
     * @param globalConnectionsLimit максимальное количество параллельных соединений
     */
    def makeLayer(
      filePath                : Path,
      from                    : LocalDate,
      to                      : LocalDate,
      globalConnectionsLimit  : Int,
      doDownloadTracks        : Boolean)
    : ZLayer[Console with Blocking, Throwable, Config] = {

      val make = for {
        _           <- putStr(s"Loading config from $filePath. ")

        jarPath     <- ZIO.effectTotal(
                          Path(new File(Config.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).getPath).parent.get)
        cfgPath     = jarPath / filePath

        jsonString  <- Files.readAllBytes(cfgPath).map(c => new String(c.toArray, StandardCharsets.UTF_8))
        json        <- parse(jsonString).toZio.mapError(s => new Exception(s))
        description <- json.as[ConfigDescription].toZio

        artistsRegxp<- Files.readAllLines(jarPath / description.my.artistsFile)
                        .map(list => list.map(name => buildArtistRegex(name)))
        labels      <- Files.readAllLines(jarPath / description.my.labelsFile)
                        .map(list => list.map(_.trim.toLowerCase))
        _           <- putStr(s"Loaded ${artistsRegxp.length} watched artists and ${labels.length} watched labels. ")

        shit        <-  ZIO.foldLeft(description.noShit.dataFiles)(List[String]())(
                          (list, fName) => Files.readAllLines(jarPath / Path(fName)).map(list ++ _))
        _           <- putStrLn(s"Loaded ${shit.length} ignored labels.")
        localDt     <- ZIO.succeed(LocalDateTime.now())
        _           <- ensureCorrectDateRangeIsUsedWithTraxsourceFeeds(description.feeds, from, localDt.toLocalDate)
        gSemaphore  <- Semaphore.make(globalConnectionsLimit)
        targetFolder= description.previewsFolder.replace("{0}",
                       if (from == to.minusDays(1)) from.toString
                       else s"${from.toString}_${to.toString}")

      } yield new Service {
        val configDescription: ConfigDescription = description
        val dateFrom: LocalDate = from
        val dateTo: LocalDate = to
        val myArtistsRegexes: List[Regex] = artistsRegxp
        val myLabels: List[String] = labels
        val shitLabels: List[String] = shit
        val previewsBasePath: Path = jarPath / Path(targetFolder)
        val runId : LocalDateTime = localDt
        val globalConnSemaphore : Semaphore = gSemaphore
        val appPath : Path = jarPath
        val downloadTracks : Boolean = doDownloadTracks
      }

      make.toLayer
    }

    def buildArtistRegex(name : String) : Regex =
      ("(\\W|^)" + Regex.quote(name.trim.toLowerCase) + "(\\W|$)").r

    /** Traxsource "Just Added" and "DJ Top 10s" sections do not work on dates earlier then 180 days prior today.
     *  Support answered that it is so by design. */
    def ensureCorrectDateRangeIsUsedWithTraxsourceFeeds(
      feeds : List[Feed], dateFrom : LocalDate, currentDate : LocalDate)
    : Task[Unit] =
    {
      for {
        _         <- ZIO.succeed()
        problematic=  feeds.filter(feed =>
                        feed.tag == FeedTag.TraxsourceFeed &&
                          (feed.urlTemplate.startsWith("/just-added") ||
                           feed.urlTemplate.startsWith("/dj-top-10s")))
        earliest  =  currentDate.minusDays(179)
        _ <- ZIO.fail(new Exception(
          """Traxsource "Just Added" and "DJ Top 10s" sections do not work on dates earlier then 180 days prior today.
            |Please use more recent dates or remove feeds with urls starting with /just-added or /dj-top-10s
            |Problematic feeds: """.stripMargin ++ problematic.map(_.name).mkString(", ")
        )).when(problematic.nonEmpty && dateFrom.compareTo(earliest) < 0)
      } yield ()
    }
  }
}
