package vine.processing

import zio._
import zio.logging._
import vine.config.Config
import vine.data._
import vine.providers.TrackDto
import java.time.LocalDateTime

object Deduplication {

  // For intellij correct code highlighting
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  sealed trait DeduplicationResult
  final case class Duplicate(existing : Track) extends DeduplicationResult
  final case class Resumed(track : Track, failedOn : LocalDateTime) extends DeduplicationResult
  final case class InProcess(track : Track) extends DeduplicationResult
  final case class Enqueued(track : Track) extends DeduplicationResult

  def deduplicateOrEnqueue(dto: TrackDto)
  : ZIO[Config with VineDatabase with Logging, Throwable, DeduplicationResult] =
  {
    ZIO.service[DatabaseService].flatMap { db =>
      import db.profile.api._
      for {
        runId <- Config.runId
        newTrack <- dto.toDbTrackZio(Some(runId))

        transaction = for {
          duplicateOpt  <- db.tracks.filter(t => t.uniqueName === newTrack.uniqueName).result.headOption
          res0          <- duplicateOpt match {
                          // New track
                          case None =>  {
                              (db.tracks returning db.tracks.map(_.id) += newTrack)
                                .map(id => newTrack.copy(id = id))
                                .map(Enqueued)
                          }
                          case Some(existingTrack) =>
                            existingTrack.queued match {
                              // Track has been successfully downloaded earlier
                              case None => DBIO.successful(Duplicate(existingTrack))
                              // Track is being processed by another worker
                              case Some(old) if old == runId  => DBIO.successful(InProcess(existingTrack))
                              // Downloading failed on previous run
                              case Some(old) =>
                                val existingById = db.tracks.filter(_.id === existingTrack.id)
                                for {
                                  _       <- existingById.map(_.queued).update(Some(runId))
                                  updated <- existingById.result.head
                                } yield Resumed(updated, old)
                            }
                        }
        } yield res0

        result  <- db.run(transaction.transactionally)
        _       <- logDeduplicationResult(dto, result, runId)

      } yield result
    }
  }

  def markAsCompleted(deduplicationResult: DeduplicationResult): ZIO[VineDatabase, Throwable, Unit] =
  {
    def doMark(trackId : Int) =
      ZIO.service[DatabaseService].flatMap { db =>
      import db.profile.api._
      for {
        _ <- ZIO.succeed()
        transaction = for {
          existing  <- db.tracks.filter(_.id === trackId).result
          id        =  existing.headOption.get.id
          _         <- db.tracks.filter(_.id === id).map(_.queued).update(None)
        } yield ()
        _ <- db.run(transaction.transactionally)
      } yield ()
    }

    deduplicationResult match {
      case Duplicate(_)   => ZIO.succeed()
      case InProcess(_)   => ZIO.succeed()
      case Enqueued(t)    => doMark(t.id)
      case Resumed(t, _)  => doMark(t.id)
    }
  }

  private def logDeduplicationResult(dto : TrackDto, r : DeduplicationResult, currentRunId : LocalDateTime)
  : ZIO[Logging, Nothing, Unit] =
  {
    r match {
      case Enqueued(_) => ZIO.succeed()
      case Duplicate(t)  =>
        // Logging deduplication events only when artist or title are different.
        val needsLogging = t.artist != dto.artist || t.title != dto.title
        log.info(s"Track deduplicated.\n\tNew: ${dto.artist} - ${dto.title}\n\tOld: ${t.artist} - ${t.title}")
          .when(needsLogging)
      case InProcess(_) => ZIO.succeed()
      case Resumed(_, on) =>
        log.info(s"Restarting failed download: ${dto.artist} - ${dto.title}. Failed on $on (current:$currentRunId)")
    }
  }
}