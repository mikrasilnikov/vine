package pd2.web

import pd2.config.Feed
import zio._

import java.time.LocalDate

trait WebDataProvider[F <: Feed] {
    def processTracks(feed : F, dateFrom : LocalDate, dateTo : LocalDate, processAction: TrackDto => ZIO[Any, Pd2Exception, Unit]) : Task[Unit]
}
