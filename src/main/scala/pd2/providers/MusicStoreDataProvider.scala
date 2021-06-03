package pd2.providers

import pd2.config.ConfigDescription.Feed
import pd2.providers.Exceptions.{InternalConfigurationError, ServiceUnavailable, BadContentLength}
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

import java.time.LocalDate

trait MusicStoreDataProvider {

    type SttpRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

    protected val sttpClient : SttpClient.Service
    protected val providerSemaphore : Semaphore
    protected val globalSemaphore : Semaphore

    def processTracks[R, E <: Throwable](
      feed        : Feed,
      dateFrom    : LocalDate,
      dateTo      : LocalDate,
      filter      : TrackFilter,
      processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
    : ZIO[R with FilterEnv with ConsoleProgress with Clock, Throwable, Unit]

    protected val providerBasicRequest = basicRequest
      .header(HeaderNames.UserAgent,
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
      .readTimeout(concurrent.duration.Duration(30, concurrent.duration.SECONDS))

    protected val trackRequest = providerBasicRequest.response(asByteArray)

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
    protected def download(uri : Uri, progressItem : Option[ProgressItem])
    : ZIO[ConsoleProgress with Clock, Throwable, Array[Byte]] =
    {
        def send(req : SttpRequest) : ZIO[Clock, Throwable, Array[Byte]] = for {
            resp        <- sttpClient.send(req).timeoutFail(ServiceUnavailable("Timeout", uri))(5.minutes)
            data        <- resp.body.toZio.mapError(err => ServiceUnavailable(s"Status code ${resp.code.code}, $err", uri))
            lengthOpt   =  resp.headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
            _           <- ZIO.fail(BadContentLength("Bad content length", uri))
                            .unless(lengthOpt.forall(_ == data.length))
        } yield data

        for {
            req             <- ZIO.effect(providerBasicRequest.get(uri).response(asByteArray))
            schedule        =  Schedule.recurs(10) && Schedule.spaced(5.seconds)
            updateProgress  =  progressItem match {
                                case Some(item) => ConsoleProgress.updateProgressItem(item, ProgressBar.InProgress)
                                case None => ZIO.succeed()
            }
            resp            <- globalSemaphore.withPermit(providerSemaphore.withPermit(
                                send(req).tapError(_ => updateProgress)))
                                .retry(schedule)
        } yield resp
    }
}
