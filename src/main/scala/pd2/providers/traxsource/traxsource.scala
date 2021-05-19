package pd2.providers

import pd2.config.ConfigDescription.Feed.TraxsourceFeed
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
      def processTracks[R1, R2, E1 <: Throwable, E2 <: Throwable](
        feed        : TraxsourceFeed,
        dateFrom    : LocalDate,
        dateTo      : LocalDate,
        filterTrack : TrackDto => ZIO[R1, E1, Boolean],
        processTrack: (TrackDto, Array[Byte]) => ZIO[R2, E2, Unit])
      : ZIO[R1 with R2 with Clock, Throwable, Unit]
    }
  }
}
