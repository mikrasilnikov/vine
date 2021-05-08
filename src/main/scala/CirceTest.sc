import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

val intsJson = List(1, 2, 3).asJson