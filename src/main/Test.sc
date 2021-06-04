import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDate, LocalDateTime, Period}
import scala.util.matching.Regex

val artist = "Jay J"

val dateFrom = LocalDate.parse("2020-01-01")
val dateTo = LocalDate.parse("2020-12-31")
ChronoUnit.DAYS.between(dateFrom, dateTo)
Period.between(dateFrom, dateTo).getMonths

(0 until Period.between(dateFrom, dateTo).getDays).map(i => dateFrom.plusDays(i))

