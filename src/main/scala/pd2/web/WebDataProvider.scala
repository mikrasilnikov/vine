package pd2.web

import pd2.config.Feed
import zio._

trait WebDataProvider[F <: Feed] {
    def processTracks(feed : F, processAction : TrackDto => Task[Unit])
}
