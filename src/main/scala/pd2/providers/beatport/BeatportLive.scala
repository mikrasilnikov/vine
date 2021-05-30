package pd2.providers.beatport

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pd2Exception, TrackDto}
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import sttp.client3.asByteArray
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.Uri
import zio.clock.Clock
import zio.{Promise, Semaphore, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class BeatportLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service,
  providerSemaphore   : Semaphore,
  globalSemaphore     : Semaphore)
  extends Beatport.Service {

  val beatportHost = "https://www.beatport.com"

  override def processTracks[R, E <: Throwable](
    feed: Feed,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    filter: TrackFilter,
    processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
  : ZIO[R with FilterEnv with Clock, Throwable, Unit] = {
    for {
      firstPageProgress <- consoleProgress.acquireProgressItem(feed.name)
      firstPagePromise  <- Promise.make[Nothing, BeatportPage]
      firstPageFiber    <- processTracklistPage(feed, dateFrom, dateTo, 1, filter, processTrack,
                            firstPageProgress, Some(firstPagePromise)).fork
      firstPage         <- firstPagePromise.await
      remainingProgress <- consoleProgress.acquireProgressItems(feed.name, firstPage.remainingPages.length)
      _                 <- ZIO.foreachParN_(8)(firstPage.remainingPages zip remainingProgress) { case (page, p) =>
                            processTracklistPage(feed, dateFrom, dateTo, page, filter, processTrack, p, None)
                          }
      _                 <- firstPageFiber.join
    } yield ()
  }

  private def processTracklistPage[R, E <: Throwable](
    feed: Feed,
    dateFrom: LocalDate,
    dateTo: LocalDate,
    pageNum: Int,
    filter: TrackFilter,
    processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pageProgressItem: ProgressItem,
    pagePromiseOption: Option[Promise[Nothing, BeatportPage]])
  : ZIO[R with FilterEnv with Clock, Throwable, Unit] = {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum)
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page).unit)
      _                       <- consoleProgress.completeProgressItem(pageProgressItem)
      filteredTracksWithDtos  <- ZIO.filter(page.tracks.map(st => (st, st.toTrackDto(feed.name)))) { case (_, dto) => filter.check(dto) }
      tracksProgress          <- consoleProgress.acquireProgressItems(feed.name, filteredTracksWithDtos.length)
      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos zip tracksProgress) { case ((t, dto), p) =>
                                for {
                                  _ <- downloadTrack(t.previewUrl)
                                    .flatMap(bytes => processTrack(dto, bytes) *> filter.done(dto))
                                    .whenM(filter.checkBeforeProcessing(dto))
                                  _ <- consoleProgress.completeProgressItem(p)
                                } yield ()
      }
    } yield ()
  }

  private def getTracklistWebPage(feed: Feed, dateFrom: LocalDate, dateTo: LocalDate, page: Int = 1)
  : ZIO[Clock, Pd2Exception, BeatportPage] = {
    for {
      pageReq   <- buildPageRequest(beatportHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- performRequest(pageReq).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- BeatportPage.parse(pageResp).toZio
    } yield page
  }

  private def downloadTrack(trackUri: Uri): ZIO[Clock, Pd2Exception, Array[Byte]] = {
    for {
      request <- ZIO.succeed(providerBasicRequest.get(trackUri).response(asByteArray))
      bytes   <- performRequest(request)
    } yield bytes
  }
}

object BeatportLive {
  def makeLayer(maxConcurrentConnections : Int): ZLayer[Config with ConsoleProgress with SttpClient, Nothing, Beatport] =
    ZLayer.fromServicesM[Config.Service, ConsoleProgress.Service, SttpClient.Service, Any, Nothing, Beatport.Service] {
      case (config, consoleProgress, sttpClient) => for {
        providerSemaphore <- Semaphore.make(maxConcurrentConnections)
      } yield BeatportLive(consoleProgress, sttpClient, providerSemaphore, config.globalConnSemaphore)
    }
}