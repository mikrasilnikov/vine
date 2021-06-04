


import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate, LocalDateTime, Period}

val dateFrom = LocalDate.parse("2021-05-01")
val dateTo = LocalDate.parse("2021-05-03")

(0 until Period.between(dateFrom, dateTo).getDays).map(i => dateFrom.plusDays(i))


