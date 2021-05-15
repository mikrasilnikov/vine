package pd2.ui

import pd2.ui.ConsoleProgressService.ConsoleProgress
import pd2.ui.ProgressBar.ProgressBarDimensions
import zio.ZIO

import scala.language.implicitConversions

object ProgressOps {
  implicit class ProgressOps[R, E, A](zio : ZIO[R, E, A]) {
    def withProgressReporting(batchName : String) : ZIO[R with ConsoleProgress, E, A] =
      ConsoleProgressService.withProgressReporting(batchName)(zio)
  }
}
