package pd2.processing

import pd2.conlimiter.ConnectionsLimiter
import pd2.providers.MusicStoreDataProvider
import sttp.client3
import sttp.client3._
import sttp.client3.httpclient.zio.SttpClient
import sttp.model._
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
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
  : ZIO[SttpClient with ConnectionsLimiter with Logging with Clock with Blocking, Throwable, Unit] =
  {
    for {
      sttpClient  <- ZIO.service[SttpClient.Service]
      data        <- MusicStoreDataProvider.fetchWithTimeoutAndRetry(sttpClient, uri)
      _           <- createDirectory(to.parent)
      _           <- Files.writeBytes(to, Chunk.fromArray(data))
    } yield ()
  }

  private def createDirectory(dir : Option[Path]) : ZIO[Blocking with Logging, Throwable, Unit] = {
    dir match {
      case None => ZIO.unit
      case Some(d) => ZIO.ifM(Files.exists(d))(
          ZIO.unit,
          createDirectory(d.parent) *> Files.createDirectory(d))
      }
    }
}