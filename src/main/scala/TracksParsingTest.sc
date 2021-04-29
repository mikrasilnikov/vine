import java.nio.file.{Files, OpenOption, Path, StandardOpenOption}
import scala.jdk.StreamConverters.StreamHasToScala
import pd2.data.TrackParsing
import java.io.FileWriter
import java.io.PrintWriter

val dataPath = "c:\\Music-Sly\\PreviewsDownloader\\data\\tracks\\"

val _ = for {
  file <- Files.list(Path.of(dataPath))
  line <- Files.lines(file)
  (artist, title) = line.splitAt(line.indexOf('\t'))
} yield (artist, title)

val notParsing = Files.list(Path.of(dataPath))
  .flatMap(path => Files.lines(path))
  .map(line => line.splitAt(line.indexOf('\t')))
  //.limit(1000)
  .toScala(List)
  .map { case (artistStr:String, titleStr:String) => (artistStr.trim, titleStr.trim) }
  .map { case (artistStr, titleStr) =>
    val parsedArtist = TrackParsing.parseArtists(artistStr)
    val parsedTitle = TrackParsing.parseTitle(titleStr)
    (artistStr, parsedArtist, titleStr, parsedTitle)
  }
  .filter { case (_, parsedArtist, _, parsedTitle) =>
    parsedArtist.isEmpty || parsedTitle.isEmpty
  }
  .map {case (artistStr, parsedArtist, titleStr, parsedTitle) =>
    s"$artistStr - $titleStr"
  }



val pw = new PrintWriter(new FileWriter("d:\\!temp\\notParsing.txt"))
notParsing.foreach(l => pw.println(l))
pw.close()

