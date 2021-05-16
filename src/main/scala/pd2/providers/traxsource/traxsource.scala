package pd2.providers

import pd2.config.TraxsourceFeed
import pd2.providers.{Pd2Exception, TrackDto}
import zio.{Has, ZIO}
import zio.macros.accessible

import java.time.LocalDate

package object traxsource {

  type Traxsource = Has[Traxsource.Service]

  @accessible
  object Traxsource
  {
    trait Service {
      def processTracks[R](
        feed        : TraxsourceFeed,
        dateFrom    : LocalDate,
        dateTo      : LocalDate,
        processTrack: TrackDto => ZIO[R, Pd2Exception, Unit])
      : ZIO[R, Pd2Exception, Unit]
    }
  }
}
