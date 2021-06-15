package pd2

import pd2.Application.TrackMsg
import pd2.config.Config
import pd2.config.ConfigModel.Feed
import pd2.counters.Counters
import pd2.filters.{TrackFilter, getFilterByTag}
import pd2.processing.Deduplication.{Duplicate, Enqueued, InProcess, Resumed}
import pd2.providers.{TrackDto, getProviderByFeedTag}
import pd2.ui.consoleprogress.ConsoleProgress
import sttp.model.Uri
import zio.{Promise, Queue, Semaphore, ZIO}
import zio.clock.Clock
import zio.duration.durationInt
import zio.logging.log
import zio.nio.core.file.Path

package object processing {

  def processFeeds(feeds : List[Feed]) =
  {
    val feedsByPriority = feeds.groupBy(_.priority).toList.sortBy { case (p, _) => p }
    val sortedFeeds = feedsByPriority.flatMap { case (_, group) => group.sortBy(_.name) }

    for {
      _ <- ZIO.foreach_(sortedFeeds)(f => ConsoleProgress.initializeBar(f.name, List(1)))
      _ <- ZIO.foreach_(feedsByPriority) { case (_, group) => ZIO.foreachPar_(group)(processFeed) }
    } yield ()
  }

  private def processFeed(feed : Feed) = {
    for {
      folderSem   <- Semaphore.make(1)
      feedPath    <- Config.previewsBasePath.map(_ / Path(feed.name))

      (from, to)  <- Config.dateFrom <*> Config.dateTo
      workerNum   <- Config.connectionsPerHost
      provider    <- getProviderByFeedTag(feed.tag)
      filter      =  feed.filterTags.map(getFilterByTag).foldLeft(filters.withArtistAndTitle)(_ ++ _)

      queue       <- Queue.bounded[TrackMsg](300)
      completionP <- Promise.make[Nothing, Unit]

      workers     <- ZIO.forkAll(List.fill(workerNum)(
        runWorker(queue, completionP)(msg => processTrack(msg, filter, feedPath, folderSem))))

      _           <- provider.processTracks(feed, from, to, queue, completionP)

      _           <- workers.join
      _           <- queue.size.flatMap(rem => log.info(s"feed ${feed.name} completed. $rem messages remaining."))
      _           <- queue.shutdown
    } yield ()
  }

  def processTrack(
    msg         : TrackMsg,
    filter      : TrackFilter,
    targetPath  : Path,
    folderSem   : Semaphore) =
  {
    def deduplicateOrDownload(msg : TrackMsg) = for {
      dResult <- Deduplication.deduplicateOrEnqueue(msg.dto)

      fileName=  makeFileName(msg.dto)
      download=  ZIO.effect(Uri.unsafeParse(msg.dto.mp3Url))
        .flatMap(uri => Saving.downloadWithRetry(uri, targetPath / fileName, folderSem)
          .whenM(Config.downloadTracks))
      _       <- dResult match {
        case Duplicate(_)   => ZIO.succeed()
        case InProcess(_)   => ZIO.succeed()
        case Enqueued(_)    => download
        case Resumed(_, _)  => download
      }
      _       <- Deduplication.markAsCompleted(dResult)
    } yield ()

    for {
      //_ <- log.trace(s"got $msg")
      _ <- Counters.modify(msg.dto.feed, -1)
      _ <- deduplicateOrDownload(msg).whenM(filter.check(msg.dto))
        .foldCauseM( // Report error to user, continue processing
          c =>  log.error(s"Download failed\nUrl: ${msg.dto.mp3Url}\n${c.prettyPrint}") *>
            ConsoleProgress.failOne(msg.bucketRef),
          _ =>  ConsoleProgress.completeOne(msg.bucketRef))
    } yield ()
  }

  private def makeFileName(dto : TrackDto): String = {
    val durationStr = f"${dto.duration.toSeconds / 60}%02d:${dto.duration.toSeconds % 60}%02d"
    val withoutExt = s"[${dto.label}] [${dto.releaseName}] - ${dto.artist} - ${dto.title} - [$durationStr]"
    // File name length limit on Windows.
    val result = (if (withoutExt.length >= 251) withoutExt.substring(0, 251) else withoutExt) ++ ".mp3"
    result.replaceAll("([<>:\"/\\\\|?*])", "_")
  }

  private def runWorker[A, R](queue: Queue[A], stopSignal: Promise[Nothing, Unit])(process: A => ZIO[R, Throwable, Unit])
  : ZIO[R with Clock, Throwable, Unit] =
  {
    for {
      aOpt  <- queue.poll
      stop  <- stopSignal.isDone
      _ <- (aOpt, stop) match {
        case (Some(a), _) => process(a) *> runWorker(queue, stopSignal)(process)
        case (None, false) => ZIO.sleep(1.milli) *> runWorker(queue, stopSignal)(process)
        case (None, true) => ZIO.succeed()
      }
    } yield ()
  }

  // Non polling version, does not work :(
  private def runWorker1[A, R](queue: Queue[A], drainSignal: Promise[Nothing, Unit])(process : A => ZIO[R, Throwable, Unit])
  : ZIO[R, Throwable, Unit] =
  {
    def take: ZIO[R, Throwable, Unit] = for {
      msg <- queue.take.map(Some(_)) race drainSignal.await.as(None)
      _   <- msg.fold(drain)(a => process(a) *> take)
    } yield ()

    def drain: ZIO[R, Throwable, Unit] = for {
      msg <- queue.poll
      _   <- msg.fold[ZIO[R, Throwable, Unit]](ZIO.succeed())(a => process(a) *> drain)
    } yield ()

    ZIO.ifM(drainSignal.isDone)(drain, take)
  }
}
