import java.nio.file.{Files, OpenOption, Path, StandardOpenOption}
import scala.jdk.StreamConverters.StreamHasToScala
import pd2.data.TrackParsing
import pd2.data.TrackParsing.{_}

import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets

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

  s"${artistNames.mkString(", ")} - ${parsedTitle.actualTitle} ${parsedTitle.mix.map(" " + _).getOrElse("")}"

}

val parsedData = Files.list(Path.of(dataPath))
  .flatMap(path => Files.lines(path, StandardCharsets.UTF_8))
  .map(line => line.split('\t'))
  .toScala(List)
  .map(parts => (parts(0).trim, parts(1).trim))
  .map { case (artistStr, titleStr) =>
    val parsedArtist = TrackParsing.parseArtists(artistStr)
    val parsedTitle = TrackParsing.parseTitle(titleStr)
    (artistStr, parsedArtist, titleStr, parsedTitle)
  }

 val notParsedData = parsedData
  .filter { case (_, parsedArtist, _, parsedTitle) =>
    parsedArtist.isEmpty || parsedTitle.isEmpty
  }
  .map {case (artistStr, parsedArtist, titleStr, parsedTitle) =>
    s"$artistStr - $titleStr"
  }

var pw = new PrintWriter(new FileWriter("d:\\!temp\\notParsing.txt"))
notParsedData.foreach(l => pw.println(l))
pw.close()

pw = new PrintWriter(new FileWriter("d:\\!temp\\rewritten.txt"))
parsedData
  .filter { case (_, parsedArtist, _, parsedTitle) =>
    parsedArtist.isDefined && parsedTitle.isDefined
  }
  .foreach { case (artistStr, parsedArtist, titleStr, parsedTitle) =>
  val sourceName = s"$artistStr - $titleStr"
  val rewrite = rewriteTrackName(parsedArtist.get, parsedTitle.get)

  if (sourceName != rewrite) {
    pw.println(sourceName)
    pw.println(rewrite)
    pw.println()
  }
}
pw.close()
