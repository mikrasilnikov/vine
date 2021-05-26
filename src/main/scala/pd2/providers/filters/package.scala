package pd2.providers

import org.joda.time.LocalDateTime
import pd2.config.ConfigDescription.FilterTag
import pd2.config.{Config, FilterTag}
import pd2.data.{Pd2Database, Pd2DatabaseService, Track}
import zio.{Has, ZIO}
import slick.dbio._

package object filters {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  type FilterEnv = Config with Pd2Database

  trait TrackFilter { self =>
    /** Проверяет, подходит ли трек */
    def check(dto : TrackDto) : ZIO[FilterEnv, Throwable, Boolean]
    /** Если нужно, выполняет дополнительные действия после скачивания трека */
    def done(dto : TrackDto) : ZIO[FilterEnv, Throwable, Unit]

    def ++(that : TrackFilter) : TrackFilter= new TrackFilter {
      def check(dto: TrackDto): ZIO[FilterEnv, Throwable, Boolean] =
        for {
          x <- self.check(dto)
          // TODO
          y <- if (x) that.check(dto) else ZIO.succeed(false)
        } yield x && y

      def done(dto: TrackDto): ZIO[FilterEnv, Throwable, Unit] =
        self.done(dto) *> that.done(dto)
    }
  }

  val empty = new TrackFilter {
    def check(dto: TrackDto): ZIO[Any, Throwable, Boolean] = ZIO.succeed(true)
    def done(dto: TrackDto): ZIO[Any, Throwable, Unit] = ZIO.succeed()
  }

  val my = new TrackFilter {
    def check(dto: TrackDto): ZIO[Config, Throwable, Boolean] = {
      for {
        artists   <- Config.myArtists
        labels    <- Config.myLabels
        myArtist  =  artists.exists(a => dto.artist.toLowerCase.contains(a.toLowerCase)) ||
                     artists.exists(a => dto.title.toLowerCase.contains(a.toLowerCase))
        myLabel   =  labels.exists(l => l.toLowerCase == dto.label.toLowerCase)
      } yield myArtist || myLabel
    }

    def done(dto: TrackDto): ZIO[Config, Throwable, Unit] = ZIO.succeed()
  }

  val onlyNew: TrackFilter = new TrackFilter
  {
    def check(dto: TrackDto): ZIO[Config with Pd2Database, Throwable, Boolean] =
    {
      ZIO.service[Pd2DatabaseService].flatMap { db =>
        import db.profile.api._
        for {
          runId <- Config.runId
          newTrack <- dto.toDbTrackZio(Some(runId))

          transaction = for {
            existingTrackOpt <- db.tracks.filter(t => t.uniqueName === newTrack.uniqueName).result

            res0 <- existingTrackOpt.headOption match {
              // Ранее такого трека мы не видели.
              case None => (db.tracks += newTrack) >> DBIO.successful(true)
              case Some(existingTrack) =>
                existingTrack.queued match {
                  // Такой трек раньше встречался и его уже скачали.
                  case None => DBIO.successful(false)
                  // Трек уже видели при текущем запуске (например, другой fiber его уже скачивает).
                  case Some(id) if id == runId  => DBIO.successful(false)
                  // Трек видели при предыдущем запуске, но не скачали. Например, из-за ошибки.
                  case Some(_) =>
                    db.tracks.filter(_.id === existingTrack.id).map(_.queued).update(Some(runId)) >>
                    DBIO.successful(true) // проставляем треку текущий runId
                }
            }
          } yield res0

          res1 <- db.run(transaction.transactionally)
        } yield res1
      }
    }

    def done(dto: TrackDto): ZIO[Pd2Database, Throwable, Unit] =
      ZIO.service[Pd2DatabaseService].flatMap { db =>
        import db.profile.api._
        for {
          uniqueName <- dto.uniqueNameZio
          transaction = for {
            existing  <- db.tracks.filter(_.uniqueName === uniqueName).result
            id        =  existing.headOption.get.id // Оно точно должно там быть
            _         <- db.tracks.filter(_.id === id).map(_.queued).update(None)
          } yield ()
          _ <- db.run(transaction.transactionally)
        } yield ()
      }
  }
}
