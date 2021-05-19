package pd2
import zio.blocking.Blocking
import zio.{Has, ZIO, ZLayer}
import zio.macros.accessible
import zio.nio.core.file.Path
import zio.nio.file.Files
import io.circe.parser._
import pd2.helpers.Conversions.EitherToZio
import zio.console.{Console, putStr, putStrLn}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files => JFiles, Path => JPath}
import java.time.LocalDate

package object config {

  type Config = Has[Config.Service]

  @accessible
  object Config {

    trait Service {
      val configDescription : ConfigDescription
      val myArtists         : List[String]
      val myLabels          : List[String]
      val shitLabels        : List[String]
      val targetPath        : Path
    }

    def makeLayer(fileName : String, date : LocalDate) : ZLayer[Console with Blocking, Throwable, Config] = {

      val make = for {
        _           <- putStrLn(s"Loading config from $fileName...")

        jarPath     <- ZIO.effectTotal(
                          Path(new File(Config.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).getPath).parent.get)
        cfgPath     = jarPath / Path(fileName)

        jsonString  <- Files.readAllBytes(cfgPath).map(c => new String(c.toArray, StandardCharsets.UTF_8))
        json        <- parse(jsonString).toZio.mapError(s => new Exception(s))
        description <- json.as[ConfigDescription].toZio

        artists     <- Files.readAllLines(jarPath / description.my.artistsFile)
        labels      <- Files.readAllLines(jarPath / description.my.labelsFile)
        _           <- putStrLn(s"Loaded ${artists.length} watched artists and ${labels.length} watched labels")

        shit        <-  ZIO.foldLeft(description.noShit.dataFiles)(List[String]())(
                          (list, fName) => Files.readAllLines(jarPath / Path(fName)).map(list ++ _))
        _           <- putStrLn(s"Loaded ${shit.length} shit labels")

      } yield new Service {
        val configDescription: ConfigDescription = description
        val myArtists: List[String] = artists
        val myLabels: List[String] = labels
        val shitLabels: List[String] = shit
        val targetPath: Path = jarPath / Path(description.previewsFolder.replace("{0}", date.toString))
      }

      make.toLayer
    }
  }
}
