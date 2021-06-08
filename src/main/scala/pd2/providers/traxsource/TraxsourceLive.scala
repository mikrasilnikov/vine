package pd2.providers.traxsource

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.helpers.Conversions.EitherToZio
import pd2.providers.Exceptions.InternalConfigurationError
import pd2.providers.filters.{FilterEnv, TrackFilter}
import pd2.providers.{Pager, TrackDto}
import pd2.ui.consoleprogress.ConsoleProgress
import sttp.client3.httpclient.zio.SttpClient
import sttp.model.Uri
import zio.clock.Clock
import zio.logging.Logging
import zio.{Promise, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.time.LocalDate

case class TraxsourceLive(
  consoleProgress     : ConsoleProgress.Service,
  sttpClient          : SttpClient.Service)
  extends Traxsource.Service
{
  private val traxsourceHost = "https://www.traxsource.com"

  def processTracklistPage[R, E <: Throwable](
    feed             : Feed,
    dateFrom         : LocalDate,
    dateTo           : LocalDate,
    pageNum          : Int,
    filter           : TrackFilter,
    processTrack     : (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
    pagePromiseOption: Option[Promise[Throwable, Option[Pager]]])
  : ZIO[R with FilterEnv with Clock with Logging with ConnectionsLimiter, Throwable, Unit] =
  {
    for {
      page                    <- getTracklistWebPage(feed, dateFrom, dateTo, pageNum)
                                  .tapError(e => pagePromiseOption.fold(ZIO.succeed())(promise => promise.fail(e).unit))
      _                       <- pagePromiseOption.fold(ZIO.succeed())(_.succeed(page.pager).unit)

      serviceTracks           <- if (page.trackIds.nonEmpty) getServiceData(page.trackIds, feed.name)
                                 else ZIO.succeed(List())

      filteredTracksWithDtos  <- ZIO.filter(serviceTracks.map(st => (st, st.toTrackDto))) { case (_, dto) => filter.check(dto) }

      _                       <- ZIO.foreachParN_(8)(filteredTracksWithDtos) { case (t, dto) =>
                                  (for {
                                    downloadResult <- downloadTrack(t.mp3Url)
                                    _ <- downloadResult match {
                                      case TrackDownloadResult.Success(bytes) =>
                                          processTrack(dto, bytes) *>
                                          filter.done(dto)
                                      case TrackDownloadResult.Failure =>
                                          filter.done(dto)
                                      case TrackDownloadResult.Skipped =>
                                          filter.done(dto)
                                    }
                                  } yield ()).whenM(filter.checkBeforeProcessing(dto))
                                }
    } yield ()
  }

  private def getTracklistWebPage(
    feed : Feed, dateFrom : LocalDate, dateTo : LocalDate, page: Int = 1)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, TraxsourcePage] =
  {
    for {
      uri       <- buildPageUri(traxsourceHost, feed.urlTemplate, dateFrom, dateTo, page).toZio
      pageResp  <- download(uri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      page      <- TraxsourcePage.parse(pageResp).toZio
    } yield page
  }

  private def getServiceData(trackIds : List[Int], feed : String)
  : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, List[TraxsourceServiceTrack]] =
  {
    for {
      serviceUri  <- buildTraxsourceServiceRequest(trackIds).toZio
      serviceResp <- download(serviceUri).map(bytes => new String(bytes, StandardCharsets.UTF_8))
      tracks      <- TraxsourceServiceTrack.fromServiceResponse(serviceResp, feed).toZio
    } yield tracks
  }

  private def buildTraxsourceServiceRequest(trackIds : List[Int]) : Either[Throwable, Uri] =
  {
    val uriStr = s"https://w-static.traxsource.com/scripts/playlist.php?tracks=${trackIds.mkString(",")}"
    Uri.parse(uriStr).left.map(msg => InternalConfigurationError(msg))
  }
}

object TraxsourceLive {
  def makeLayer : ZLayer[Config with ConsoleProgress with SttpClient, Nothing, Traxsource] =
    ZLayer.fromServices[ConsoleProgress.Service, SttpClient.Service, Traxsource.Service] {
      (consoleProgress, sttpClient) => TraxsourceLive(consoleProgress, sttpClient)
  }
}