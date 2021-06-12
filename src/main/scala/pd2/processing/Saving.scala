package pd2.processing

import pd2.conlimiter.ConnectionsLimiter
import pd2.helpers.Conversions._
import pd2.providers.Exceptions._
import sttp.client3
import sttp.client3._
import sttp.client3.httpclient.zio.SttpClient
import sttp.model._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.logging._
import zio.nio.core.file._
import zio.nio.file.Files

object Saving {

  type SttpBytesRequest = RequestT[client3.Identity, Either[String, Array[Byte]], Any]

  val requestBase = basicRequest
    .header(
      HeaderNames.UserAgent,
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
    .response(asByteArray)

  def downloadWithRetry(uri : Uri, to : Path, folderSem : Semaphore)
  : ZIO[ConnectionsLimiter with SttpClient with Logging with Clock with Blocking, Throwable, Unit] =
  {
    import sttp.client3.httpclient.zio._
    def perform(req : SttpBytesRequest) = for {
      resp        <- send(req).timeoutFail(ServiceUnavailable("Timeout", uri))(5.minutes)
      data        <- resp.body.toZio.mapError(err => ServiceUnavailable(s"Status code ${resp.code.code}, $err", uri))
      lengthOpt   =  resp.headers.find(_.name == HeaderNames.ContentLength).map(_.value.toInt)
      _           <- ZIO.fail(BadContentLength("Bad content length", uri)).unless(lengthOpt.forall(_ == data.length))
    } yield data

    for {
      req       <- ZIO.effect(requestBase.get(uri).response(asByteArray))
      schedule  =  Schedule.recurs(10) && Schedule.spaced(5.seconds)
      resp      <- ConnectionsLimiter.withPermit(uri) {
                      log.trace(s"$uri") *>
                      perform(req).tapError( e =>log.warn(s"Retrying\n$uri\n(failed with ${e.toString})"))
                  }.retry(schedule)
                  .tapError(e => log.warn(s"Could not download $uri, error: $e"))

      _         <- folderSem.withPermit(Files.createDirectory(to.parent.get).whenM(Files.notExists(to.parent.get)))
      _         <- Files.writeBytes(to, Chunk.fromArray(resp))
    } yield ()
  }
}
