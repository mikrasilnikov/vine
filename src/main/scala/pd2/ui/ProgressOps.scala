package pd2.ui

import pd2.ui.ConsoleUIService.ConsoleUI
import zio.ZIO
import scala.language.implicitConversions

object ProgressOps {

  implicit class ProgressOps[R, E, A](zio : ZIO[R, E, A]) {
    def withProgressReporting(batchName : String) : ZIO[R with ConsoleUI, E, A] =
      ConsoleUIService.withProgressReporting(batchName)(zio)
  }

  //implicit def toProgressOps[R, E, A](zio: ZIO[R, E, A]): ProgressOps[R, E, A] = new ProgressOps(zio)
}
