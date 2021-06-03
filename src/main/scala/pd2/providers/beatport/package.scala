package pd2.providers

import pd2.config.ConfigDescription.Feed
import pd2.providers.filters._
import pd2.ui.consoleprogress._
import zio.{Has, ZIO}
import zio.clock.Clock
import zio.macros.accessible

import java.time.LocalDate

package object beatport {
  type Beatport = Has[Beatport.Service]

  @accessible
  object Beatport
  {
    trait Service extends MusicStoreDataProvider {
      def processTracks[R, E <: Throwable](
        feed        : Feed,
        dateFrom    : LocalDate,
        dateTo      : LocalDate,
        filter      : TrackFilter,
        processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
      : ZIO[R with FilterEnv with ConsoleProgress with Clock, Throwable, Unit]
    }
  }
}
