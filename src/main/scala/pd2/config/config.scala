package pd2
import zio.blocking.Blocking
import zio.{Has, Semaphore, ZIO, ZLayer}
import zio.macros.accessible
import zio.nio.core.file.Path
import zio.nio.file.Files
import io.circe.parser._
import pd2.helpers.Conversions.EitherToZio
import zio.console.{Console, putStr, putStrLn}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files => JFiles, Path => JPath}
import java.time.{LocalDate, LocalDateTime}

package object config {

  type Config = Has[Config.Service]

  @accessible
  object Config {

    trait Service {
      def configDescription   : ConfigDescription
      def dateFrom            : LocalDate
      def dateTo              : LocalDate
      def myArtists           : List[String]
      def myLabels            : List[String]
      def shitLabels          : List[String]
      def previewsBasePath    : Path
      def runId               : LocalDateTime
      def globalConnSemaphore : Semaphore
    }

    /**
     * @param filePath относительный путь к файлу конфигурации
     * @param from стартовая дата релиза треков (включительно)
     * @param to конечная дата релиза треков (не включительно)
     * @param globalConnectionsLimit максимальное количество параллельных соединений
     */
    def makeLayer(
      filePath : Path,
      from : LocalDate,
      to : LocalDate,
      globalConnectionsLimit: Int)
    : ZLayer[Console with Blocking, Throwable, Config] = {

      val make = for {
        _           <- putStrLn(s"Loading config from $filePath...")

        jarPath     <- ZIO.effectTotal(
                          Path(new File(Config.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).getPath).parent.get)
        cfgPath     = jarPath / filePath

        jsonString  <- Files.readAllBytes(cfgPath).map(c => new String(c.toArray, StandardCharsets.UTF_8))
        json        <- parse(jsonString).toZio.mapError(s => new Exception(s))
        description <- json.as[ConfigDescription].toZio

        artists     <- Files.readAllLines(jarPath / description.my.artistsFile)
        labels      <- Files.readAllLines(jarPath / description.my.labelsFile)
        _           <- putStrLn(s"Loaded ${artists.length} watched artists and ${labels.length} watched labels")

        shit        <-  ZIO.foldLeft(description.noShit.dataFiles)(List[String]())(
                          (list, fName) => Files.readAllLines(jarPath / Path(fName)).map(list ++ _))
        _           <- putStrLn(s"Loaded ${shit.length} shit labels")
        localDt     <- ZIO.succeed(LocalDateTime.now())
        gSemaphore  <- Semaphore.make(globalConnectionsLimit)
        targetFolder= description.previewsFolder.replace("{0}",
                       if (from == to) from.toString
                       else s"${from.toString}_${to.toString}")

      } yield new Service {
        val configDescription: ConfigDescription = description
        val dateFrom: LocalDate = from
        val dateTo: LocalDate = to
        val myArtists: List[String] = artists
        val myLabels: List[String] = labels
        val shitLabels: List[String] = shit
        val previewsBasePath: Path = jarPath / Path(targetFolder)
        val runId : LocalDateTime = localDt
        val globalConnSemaphore : Semaphore = gSemaphore
      }

      make.toLayer
    }

  }
}
