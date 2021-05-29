package pd2.providers

import pd2.config.ConfigDescription.Feed.BeatportFeed
import pd2.providers.filters._
import zio.{Has, ZIO}
import zio.clock.Clock
import zio.macros.accessible

import java.time.LocalDate

package object beatport {
  type Beatport = Has[Beatport.Service]

  @accessible
  object Beatport
  {
    trait Service extends WebDataProvider[BeatportFeed] {
      def processTracks[R, E <: Throwable](
        feed        : BeatportFeed,
        dateFrom    : LocalDate,
        dateTo      : LocalDate,
        filter      : TrackFilter,
        processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
      : ZIO[R with FilterEnv with Clock, Throwable, Unit]
    }
  }
}
