package pd2.providers

import pd2.config.ConfigDescription.FilterTag
import pd2.config.Config
import pd2.data.{DatabaseService, Pd2Database, Track}
import zio.{Has, ZIO}
import slick.dbio._
import zio.logging.{Logging, log}

import java.time.{Duration, LocalDateTime}
import scala.util.matching.Regex

package object filters {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  type FilterEnv = Config with Pd2Database with Logging

  trait TrackFilter { self =>
    /** Проверяет, подходит ли трек, но не вносит никаких изменений в состояние (например, не пишет ничего в базу) */
    def check(dto : TrackDto) : ZIO[FilterEnv, Throwable, Boolean]

    /** Проверяет, подходит ли трек, но может изменять состояние (писать в базу) */
    def checkBeforeProcessing(dto : TrackDto) : ZIO[FilterEnv, Throwable, Boolean]

    /** Если нужно, выполняет дополнительные действия после обработки трека трека */
    def done(dto : TrackDto) : ZIO[FilterEnv, Throwable, Unit]

    def ++(that : TrackFilter) : TrackFilter = new TrackFilter {
      def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] =
        for {
          x <- self.check(dto)
          // Чтобы не проверять второй фильтр, если первый вернул false
          y <- if (x) that.check(dto) else ZIO.succeed(false)
        } yield x && y

      override def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] =
        for {
          x <- self.checkBeforeProcessing(dto)
          y <- that.checkBeforeProcessing(dto)
        } yield x && y

      def done(dto: TrackDto): ZIO[FilterEnv, Throwable, Unit] =
        self.done(dto) *> that.done(dto)
    }
  }

  val empty : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[Any, Throwable, Boolean] = ZIO.succeed(true)
    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = ZIO.succeed(true)
    def done(dto: TrackDto): ZIO[Any, Throwable, Unit] = ZIO.succeed()
  }

  val withArtistAndTitle : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] =
      for {
        result  <- ZIO.succeed(dto.artist.nonEmpty && dto.title.nonEmpty)
        _       <- log.warn(s"Missing required field for track $dto").unless(result)
    } yield result
    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = check(dto)
    def done(dto: TrackDto): ZIO[FilterEnv, Throwable, Unit] = ZIO.succeed()
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
    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = check(dto)
    def done(dto: TrackDto): ZIO[Config, Throwable, Unit] = ZIO.succeed()
  }

  val ignoredLabels : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = for {
      shitLabels <- Config.shitLabels
    } yield !shitLabels.map(_.toLowerCase).contains(dto.label.toLowerCase)

    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = check(dto)
    def done(dto: TrackDto): ZIO[FilterEnv, Throwable, Unit] = ZIO.succeed()
  }

  val noEdits : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = for {
      minDuration <- Config.configDescription.map(desc => Duration.ofSeconds(desc.noEdits.minTrackDurationSeconds))
    } yield dto.duration.compareTo(minDuration) >= 0

    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = check(dto)
    def done(dto: TrackDto): ZIO[FilterEnv, Throwable, Unit] = ZIO.succeed()
  }

  def getFilterByTag(tag : FilterTag) : TrackFilter =
    tag match {
      case FilterTag.My             => my
      case FilterTag.IgnoredLabels  => ignoredLabels
      case FilterTag.NoEdits        => noEdits
      // Deprecated
      case FilterTag.NoCompilations => empty
      case FilterTag.OnlyNew        => empty
    }
}
