import java.time.LocalDate

val regex = "(\\w+):\\s"

"track_id: \"8803989\",".replaceAll(regex, " \"$1\": ")