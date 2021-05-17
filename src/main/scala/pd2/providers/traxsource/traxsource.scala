package pd2.providers

import pd2.config.TraxsourceFeed
import pd2.providers.{Pd2Exception, TrackDto}
import zio.clock.Clock
import zio.{Has, ZIO}
import zio.macros.accessible

import java.time.LocalDate

package object traxsource {

  type Traxsource = Has[Traxsource.Service]

  @accessible
  object Traxsource
  {
    trait Service {
      def processTracks[R, E <: Throwable](
        feed        : TraxsourceFeed,
        dateFrom    : LocalDate,
        dateTo      : LocalDate,
        processTrack: TrackDto => ZIO[R, E, Unit])
      : ZIO[R with Clock, Throwable, Unit]
    }
  }
}
