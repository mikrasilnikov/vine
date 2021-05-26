package pd2.providers

import pd2.config.ConfigDescription.Feed.TraxsourceFeed
import pd2.config.FilterTag
import pd2.providers.filters.{FilterEnv, TrackFilter}
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
        filter      : TrackFilter,
        processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
      : ZIO[R with FilterEnv with Clock, Throwable, Unit]
    }
  }
}
