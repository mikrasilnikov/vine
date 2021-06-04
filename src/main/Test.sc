import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate, LocalDateTime, Period}
import scala.util.matching.Regex

val artist = "Jay J"

Regex.quote(artist)
val regex = ("(\\W|^)" + Regex.quote(artist) + "(\\W|$)").r

regex.findFirstIn("Jay J")
regex.findFirstIn("Jay Jayson")
regex.findFirstIn("Jay J, Artist2")
regex.findFirstIn("Artistq&Jay J, Artist2")
regex.findFirstIn("Artistq & Jay J")

