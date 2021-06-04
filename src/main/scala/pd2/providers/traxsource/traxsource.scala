package pd2.providers

import pd2.config.ConfigDescription.Feed
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.ui.consoleprogress._
import zio.clock.Clock
import zio.logging.Logging
import zio.{Has, ZIO}
import zio.macros.accessible

import java.time.LocalDate

package object traxsource {

  type Traxsource = Has[Traxsource.Service]

  @accessible
  object Traxsource
  {
    trait Service extends MusicStoreDataProvider {
      def processTracks[R, E <: Throwable](
        feed        : Feed,
        dateFrom    : LocalDate,
        dateTo      : LocalDate,
        filter      : TrackFilter,
        processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
      : ZIO[R with FilterEnv with ConsoleProgress with Clock with Logging, Throwable, Unit]
    }
  }
}
