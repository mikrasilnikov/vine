package pd2

import java.nio.file.{Files, OpenOption, Path, StandardOpenOption}
import scala.jdk.StreamConverters.StreamHasToScala
import pd2.data.TrackParsing
import pd2.data.TrackParsing.{_}

import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

object DataImport {
  def main(args: Array[String]): Unit = {

    val dataPath = "c:\\Music-Sly\\PreviewsDownloader\\data\\tracks\\"

    def getAllNames(artist : Artist) : List[String] = artist match {
      case Single(name) => List(name)
      case Feat(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
      case Coop(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
      case Pres(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
    }

    def rewriteTrackName(parsedArtists : List[Artist], parsedTitle : Title) : String = {

      val artistNamesFromArtistFiled = parsedArtists.flatMap(a => getAllNames(a))

      val featuredArtistNamesO = for {
        aList <- parsedTitle.featuredArtist
      } yield aList.flatMap(a => getAllNames(a))

      val artistNames =
        (artistNamesFromArtistFiled ++
          featuredArtistNamesO.orElse(Some(List[String]())).get)
          .distinct
          .sorted

      s"${artistNames.mkString(", ")} - ${parsedTitle.actualTitle}${parsedTitle.mix.map(" " + _).getOrElse("")}"
    }

    val parsedData = Files.list(Path.of(dataPath))
      .flatMap(path => Files.lines(path, StandardCharsets.UTF_8))
      .map(line => line.split('\t'))
      .toScala(List)
      .map(parts => (parts(0).trim, parts(1).trim))
      .map { case (artistStr, titleStr) =>
        val parsedArtist = TrackParsing.parseArtists(artistStr)
        val parsedTitle = TrackParsing.parseTitle(titleStr)

        val rewritten = for {
          artists <- parsedArtist
          title <- parsedTitle
        } yield rewriteTrackName(artists, title)

        (artistStr, parsedArtist, titleStr, parsedTitle, rewritten)
      }

    val notParsedData = parsedData
      .filter { case (_, parsedArtist, _, parsedTitle, _) =>
        parsedArtist.isEmpty || parsedTitle.isEmpty
      }
      .map {case (artistStr, parsedArtist, titleStr, parsedTitle, _) =>
        s"$artistStr - $titleStr"
      }

    var pw = new PrintWriter(new FileWriter("d:\\!temp\\notParsing.txt"))
    notParsedData.foreach(l => pw.println(l))
    pw.close()

    pw = new PrintWriter(new FileWriter("d:\\!temp\\rewritten.txt"))

    parsedData
      .filter   { case (_,_,_,_,rewritten) => rewritten.isDefined }
      .groupBy  { case (_,_,_,_,rewritten) => rewritten.get }
      .filter   { case (_, items) => items.length > 2 }
      .foreach  { case (key, items) =>

          pw.println(key)

          items.foreach { case (artistStr, _, titleStr, _, _) =>
            pw.println(s"$artistStr - $titleStr")
          }

        pw.println()
      }

    pw.close()
  }
}
