package pd2.providers

import org.joda.time.LocalDateTime
import pd2.config.ConfigDescription.FilterTag
import pd2.config.Config
import pd2.data.{DatabaseService, Pd2Database, Track}
import zio.{Has, ZIO}
import slick.dbio._

import java.time.Duration

package object filters {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  type FilterEnv = Config with Pd2Database

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

  val withArtistAndTitle = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = ZIO.succeed(dto.artist.nonEmpty && dto.title.nonEmpty)
    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = check(dto)
    def done(dto: TrackDto): ZIO[FilterEnv, Throwable, Unit] = ZIO.succeed()
  }

  val my  : TrackFilter = new TrackFilter {
    def check(dto: TrackDto): ZIO[Config, Throwable, Boolean] = {
      for {
        artists   <- Config.myArtists
        labels    <- Config.myLabels
        myArtist  =  artists.exists(a => dto.artist.toLowerCase.contains(a.toLowerCase)) ||
                     artists.exists(a => dto.title.toLowerCase.contains(a.toLowerCase))
        myLabel   =  labels.exists(l => l.toLowerCase == dto.label.toLowerCase)
      } yield myArtist || myLabel
    }
    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = check(dto)
    def done(dto: TrackDto): ZIO[Config, Throwable, Unit] = ZIO.succeed()
  }

  val onlyNew : TrackFilter = new TrackFilter
  {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = checkCore(dto, false)
    def checkBeforeProcessing(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = checkCore(dto, true)

    def done(dto: TrackDto): ZIO[Pd2Database, Throwable, Unit] =
      ZIO.service[DatabaseService].flatMap { db =>
        import db.profile.api._
        for {
          uniqueName <- dto.uniqueNameZio
          transaction = for {
            existing  <- db.tracks.filter(_.uniqueName === uniqueName).result
            id        =  existing.headOption.get.id // Если его там нет, то пусть падает
            _         <- db.tracks.filter(_.id === id).map(_.queued).update(None)
          } yield ()
          _ <- db.run(transaction.transactionally)
        } yield ()
      }

    private def checkCore(dto: TrackDto, updateDb : Boolean): ZIO[Config with Pd2Database, Throwable, Boolean] =
    {
      ZIO.service[DatabaseService].flatMap { db =>
        import db.profile.api._
        for {
          runId <- Config.runId
          newTrack <- dto.toDbTrackZio(Some(runId))

          transaction = for {
            existingTrackOpt <- db.tracks.filter(t => t.uniqueName === newTrack.uniqueName).result
            //_ <- DBIO.successful(println(existingTrackOpt))
            res0 <- existingTrackOpt.headOption match {
              // Ранее такого трека мы не видели.
              case None =>  {
                if (updateDb) (db.tracks += newTrack) >> DBIO.successful(true)
                else DBIO.successful(true)
              }
              case Some(existingTrack) =>
                existingTrack.queued match {
                  // Такой трек раньше встречался и его уже скачали.
                  case None => DBIO.successful(false)
                  // Трек уже видели при текущем запуске (например, другой fiber его уже скачивает).
                  case Some(id) if id == runId  => DBIO.successful(false)
                  // Трек видели при предыдущем запуске, но не скачали. Например, из-за ошибки.
                  case Some(_) => {
                    if (updateDb)
                      // проставляем треку текущий runId
                      db.tracks.filter(_.id === existingTrack.id).map(_.queued).update(Some(runId)) >> DBIO.successful(true)
                    else
                      DBIO.successful(true)
                  }
                }
            }
          } yield res0

          res1 <- db.run(transaction.transactionally)
        } yield res1
      }
    }
  }

  val ignoredLabels = new TrackFilter {
    def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] = for {
      shitLabels <- Config.shitLabels
    } yield shitLabels.map(_.toLowerCase).contains(dto.label.toLowerCase)

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
      case FilterTag.NoCompilations => empty
      case FilterTag.IgnoredLabels  => ignoredLabels
      case FilterTag.NoEdits        => noEdits
      case FilterTag.OnlyNew        => onlyNew
    }
}
