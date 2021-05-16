package pd2.providers

import pd2.config.Feed
import pd2.ui.ProgressBar.ProgressBarDimensions
import pd2.ui.consoleprogress.ConsoleProgress
import sttp.client3.httpclient.zio.SttpClient
import zio._

import java.time.LocalDate

trait WebDataProvider[F <: Feed] {
    def processTracks(
      feed : F,
      dateFrom : LocalDate,
      dateTo : LocalDate,
      processAction: TrackDto => ZIO[Any, Pd2Exception, Unit])
    : ZIO[SttpClient with ConsoleProgress, Pd2Exception, Unit]
}
