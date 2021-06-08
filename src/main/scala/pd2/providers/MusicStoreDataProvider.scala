package pd2.providers

import pd2.config.Config
import pd2.config.ConfigDescription.Feed
import pd2.conlimiter.ConnectionsLimiter
import pd2.providers.Exceptions.{BadContentLength, InternalConfigurationError, ServiceUnavailable}
import pd2.providers.filters.{FilterEnv, TrackFilter}
import sttp.client3
import sttp.client3.httpclient.zio.SttpClient
import sttp.client3.{Empty, RequestT, asByteArray, asString, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import pd2.helpers.Conversions._
import pd2.ui.ProgressBar
import pd2.ui.consoleprogress.ConsoleProgress
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import zio.logging._

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, Period}

trait MusicStoreDataProvider {

    type SttpBytesRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

    protected val consoleProgress   : ConsoleProgress.Service
    protected val sttpClient        : SttpClient.Service

    protected val providerBasicRequest: RequestT[Empty, Either[String, String], Any] = basicRequest
      .header(HeaderNames.UserAgent,
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
      .readTimeout(concurrent.duration.Duration(30, concurrent.duration.SECONDS))

    protected val trackRequest: RequestT[Empty, Either[String, Array[Byte]], Any] =
        providerBasicRequest.response(asByteArray)

    /** Результат скачиывания трека. Успех, ошибка или пропуск.
     *  Пропуски возвращаются, если пользователь явно указал, что не
     *  хочет скачивать треки, указав соответствующий аргумент к. строки. */
    sealed trait TrackDownloadResult
    object TrackDownloadResult {
        final case class Success(data : Array[Byte]) extends TrackDownloadResult
        final case object Failure extends TrackDownloadResult
        final case object Skipped extends TrackDownloadResult
    }


    def processTracks[R , E <: Throwable](
      feed          : Feed,
      dateFrom      : LocalDate,
      dateTo        : LocalDate,
      filter        : TrackFilter,
      processTrack  : (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
    : ZIO[R with FilterEnv with Clock with Logging with ConnectionsLimiter, Throwable, Unit] = {
        // Если фид от даты не зависит (например, это топ-100), то достаточно один раз скачать выборку,
        // указав любую дату (дата все равно не попадет в url при подстановке).
        if (!feed.dependsOnDate || dateFrom.plusDays(1) == dateTo) {
            processSingleDate(feed, dateFrom, filter, processTrack)
        } else {
            val dates = (0L until ChronoUnit.DAYS.between(dateFrom, dateTo)).map(i => dateFrom.plusDays(i))
            ZIO.foreachParN_(8)(dates)(date => processSingleDate(feed, date, filter, processTrack))
        }
    }

    private def processSingleDate[R , E <: Throwable](
      feed          : Feed,
      date          : LocalDate,
      filter        : TrackFilter,
      processTrack  : (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
    : ZIO[R with FilterEnv with Clock with Logging with ConnectionsLimiter, Throwable, Unit] =
    {
        for {
            progress          <- consoleProgress.acquireProgressItem(feed.name)
            pagerPromise      <- Promise.make[Throwable, Option[Pager]]
            firstPageFiber    <- processTracklistPage(
                                    feed, date, date.plusDays(1), 1,
                                    filter, processTrack, progress, Some(pagerPromise))
                                 .fork
            pager             <- pagerPromise.await
            remainingPages    =  pager.fold(Nil:List[Int])(_.remainingPages)
            remainingProgress <- consoleProgress.acquireProgressItems(feed.name, remainingPages.length)
            _                 <- ZIO.foreachParN_(8)(remainingPages zip remainingProgress) { case (page, progress) =>
                                    processTracklistPage(feed, date, date.plusDays(1), page,
                                    filter, processTrack, progress, None)
                                }
            _                 <- firstPageFiber.join
            // Если при обработке фида вообще ничего не скачивалось, то ProgressBar будет стоять на 0%.
            // Если взять 1 ProgressItem и сразу отметить его как законченный, то ProgressBar будет показывать 100%
            lastProgress      <- consoleProgress.acquireProgressItem(feed.name)
            _                 <- consoleProgress.completeProgressItem(lastProgress)
        } yield ()
    }

    def processTracklistPage[R, E <: Throwable](
      feed             : Feed,
      dateFrom         : LocalDate,
      dateTo           : LocalDate,
      pageNum          : Int,
      filter           : TrackFilter,
      processTrack     : (TrackDto, Array[Byte]) => ZIO[R, E, Unit],
      pageProgressItem : ProgressItem,
      pagePromiseOption: Option[Promise[Throwable, Option[Pager]]])
    : ZIO[R with FilterEnv with Clock with Logging with ConnectionsLimiter, Throwable, Unit]

    private[providers] def buildPageUri(
      host        : String,
      urlTemplate : String,
      dateFrom    : LocalDate,
      dateTo      : LocalDate,
      page        : Int = 1)
    : Either[Throwable, Uri] =
    {
        val pageParam = if (page != 1) s"&page=$page" else ""

        val uriStr =
            host ++
              urlTemplate
                .replace("{0}", dateFrom.toString)
                .replace("{1}", dateTo.toString) ++
              pageParam

        Uri.parse(uriStr)
          .left.map(msg => InternalConfigurationError(s"Не удалось приготовить ссылку по шаблону $urlTemplate, $msg"))
    }

    /**
     * Скачивает данные по ссылке. Использует таймаут на запрос, не-200 коды считаются ошибками.
     * Делает 10 повторных попыток при любых ошибках. Если передан progressItem, выставляет его
     * в состояние InProgress (анимация /-\|) при первой повторной попытке. После 10 неудачных
     * попыток возващает последнюю ошибку.
     */
    protected def download(uri : Uri, progressItem : ProgressItem)
    : ZIO[Clock with Logging with ConnectionsLimiter, Throwable, Array[Byte]] =
    {
        def send(req : SttpBytesRequest) : ZIO[Clock, Throwable, Array[Byte]] = for {
            resp        <- sttpClient.send(req).timeoutFail(ServiceUnavailable("Timeout", uri))(5.minutes)
            data        <- resp.body.toZio.mapError(err => ServiceUnavailable(s"Status code ${resp.code.code}, $err", uri))
            lengthOpt   =  resp.headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
            _           <- ZIO.fail(BadContentLength("Bad content length", uri))
                            .unless(lengthOpt.forall(_ == data.length))
        } yield data

        for {
            req             <- ZIO.effect(providerBasicRequest.get(uri).response(asByteArray))
            schedule        =  Schedule.recurs(10) && Schedule.spaced(5.seconds)
            updateProgress  =  consoleProgress.updateProgressItem(progressItem, ProgressBar.InProgress)
            resp            <- ConnectionsLimiter.withPermit(uri) {
                                    log.trace(s"$uri") *>
                                    send(req).tapError { e =>
                                        log.warn(s"Retrying\n$uri\n(failed with ${e.toString})") *>
                                        updateProgress }
                                }
                                .retry(schedule)
                                .tapError(e => log.warn(s"Could not download $uri, error: $e"))
        } yield resp
    }

    protected def downloadTrack(trackUri: Uri, progressItem : ProgressItem)
    : ZIO[Clock with Logging with Config with ConnectionsLimiter, Throwable, TrackDownloadResult] = {
        for {
            downloadTrack   <- Config.downloadTracks
            result          <- if (downloadTrack)
                                    download(trackUri, progressItem)
                                        .map(TrackDownloadResult.Success(_))
                                        .orElseSucceed(TrackDownloadResult.Failure)
                               else ZIO.succeed(TrackDownloadResult.Skipped)
        } yield result

    }
}
