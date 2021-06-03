
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}
import scala.util.matching.Regex

"01-my-traxsource"
val prefix = raw"(\d\d).+".r
"01-my-traxsource" match {
  case prefix(digits) => println(digits.toInt)
}

