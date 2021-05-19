package pd2.providers

import pd2.config.{Config, FilterTag}
import zio.ZIO

package object filters {
  trait TrackFilter[F <: FilterTag] {
    def filter[R, E](dto : TrackDto) : ZIO[R with Config, E, Boolean]
  }
}
