package pd2

import zio._
import zio.logging._
import pd2.data._
import pd2.providers._
import pd2.config.Config
import pd2.config.ConfigModel._
import java.time._

package object filters {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  type FilterEnv = Config with Pd2Database with Logging

  trait TrackFilter { self =>
    def check(dto : TrackDto) : ZIO[FilterEnv, Throwable, Boolean]

    def ++(that : TrackFilter) : TrackFilter = new TrackFilter {
      def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] =
        for {
          x <- self.check(dto)
          y <- that.check(dto)
        } yield x && y
    }
  }

  val empty : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[Any, Throwable, Boolean] = ZIO.succeed(true)
  }

  val withArtistAndTitle : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] =
      for {
        result  <- ZIO.succeed(dto.artist.nonEmpty && dto.title.nonEmpty)
        _       <- log.warn(s"Missing required field for track $dto").unless(result)
    } yield result
  }

  val my : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[Config, Throwable, Boolean] = {
      for {
        artistsRegexes  <- Config.myArtistsRegexes
        labels          <- Config.myLabels
        dtoArtistLower  = dto.artist.toLowerCase
        dtoTitleLower   = dto.title.toLowerCase

        myArtist = artistsRegexes.exists { regex =>
          regex.findFirstIn(dtoArtistLower).isDefined ||
          regex.findFirstIn(dtoTitleLower).isDefined
        }

        myLabel = labels.contains(dto.label.toLowerCase)
      } yield myArtist || myLabel
    }
  }

  val ignoredLabels : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = for {
      shitLabels <- Config.shitLabels
    } yield !shitLabels.map(_.toLowerCase).contains(dto.label.toLowerCase)
  }

  val noEdits : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = for {
      minDuration <- Config.sourcesConfig.map(desc => Duration.ofSeconds(desc.filtersConfig.noEdits.minTrackDurationSeconds))
    } yield dto.duration.compareTo(minDuration) >= 0
  }

  def getFilterByTag(tag : FilterTag) : TrackFilter =
    tag match {
      case FilterTag.My             => my
      case FilterTag.IgnoredLabels  => ignoredLabels
      case FilterTag.NoEdits        => noEdits
      case FilterTag.Empty          => empty
    }
}
