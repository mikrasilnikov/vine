package pd2.providers

import pd2.config.Feed
import pd2.providers.Pd2Exception.{InternalConfigurationError, ServiceUnavailable, TraxsourceBadContentLength}
import pd2.providers.filters.{FilterEnv, TrackFilter}
import sttp.client3
import sttp.client3.httpclient.zio.SttpClient
import sttp.client3.{Empty, RequestT, asByteArray, basicRequest}
import sttp.model.{HeaderNames, Uri}
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import pd2.helpers.Conversions._

import java.time.LocalDate

trait WebDataProvider[F <: Feed] {

    type SttpRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

    protected val sttpClient : SttpClient.Service
    protected val providerSemaphore : Semaphore
    protected val globalSemaphore : Semaphore

    def processTracks[R, E <: Throwable](
      feed        : F,
      dateFrom    : LocalDate,
      dateTo      : LocalDate,
      filter      : TrackFilter,
      processTrack: (TrackDto, Array[Byte]) => ZIO[R, E, Unit])
    : ZIO[R with FilterEnv with Clock, Throwable, Unit]


    protected val providerBasicRequest = basicRequest
      .header(HeaderNames.UserAgent,
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
      .readTimeout(concurrent.duration.Duration(30, concurrent.duration.SECONDS))

    protected val trackRequest = providerBasicRequest.response(asByteArray)

    private[providers] def buildPageRequest(
      host        : String,
      urlTemplate : String,
      dateFrom    : LocalDate,
      dateTo      : LocalDate,
      page        : Int = 1)
    : Either[Pd2Exception, SttpRequest] =
    {
        val pageParam = if (page != 1) s"&page=$page" else ""

        val uriStr =
            host ++
              urlTemplate
                .replace("{0}", dateFrom.toString)
                .replace("{1}", dateTo.toString) ++
              pageParam

        val eitherRequest = for {
            uri <- Uri.parse(uriStr)
        } yield
            providerBasicRequest
              .get(uri)
              .response(asByteArray)

        eitherRequest
          .left.map(msg => InternalConfigurationError(msg))
    }

    protected def performRequest(request : SttpRequest) : ZIO[Clock, Pd2Exception, Array[Byte]] =
    {
        for {
            _                   <- ZIO.succeed()
            requestWithTimeout = sttpClient.send(request).timeoutFail(ServiceUnavailable("Timeout"))(1.minute)
            response            <- globalSemaphore.withPermit(
                                    providerSemaphore.withPermit(requestWithTimeout))
                                        .retry(Schedule.recurs(10) && Schedule.spaced(5.seconds))
                                        .mapError(e => ServiceUnavailable(request.uri.toString() ++ "\n" ++ e.getMessage, Some(e)))
            contentLengthOption =  response.headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
            body                <- response.body.toZio.mapError(s => ServiceUnavailable(s, None))
            _                   <- ZIO.fail(TraxsourceBadContentLength("Bad content length"))
              .unless(contentLengthOption.forall(_ == body.length))
        } yield body
    }
}
