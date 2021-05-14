package pd2.helpers

import zio.{IO, ZIO}

object Conversions {

  implicit class EitherToZio[L, R](either : Either[L, R]) {
    def toZio : ZIO[Any, L, R] = ZIO.fromEither(either)
  }

  implicit class OptionToZio[A](option : Option[A]) {
    def toZio: IO[Option[Nothing], A] = ZIO.fromOption(option)
  }

}
