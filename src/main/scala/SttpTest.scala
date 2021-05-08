
import zio._
import zio.console.Console
import sttp.client3._
import sttp.model._
import sttp.client3.httpclient.zio._

import java.io.File

object SttpTest extends zio.App {

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val request = basicRequest
      .get(uri"https://traxsource.com")
      .header(HeaderNames.UserAgent,"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
      .response(asByteArray.getRight)
      .response(asFile(new File("d:\\!temp\\test.txt")))

    val backendLayer = HttpClientZioBackend.layer()

    val sendAndPrint = for {
      response <- send(request)
      //_ <- console.putStrLn(s"Got body length:\n${response.body.length}")
      _ <- console.putStrLn(s"Got cookies:\n${response.cookies.toString()}")
    } yield ()

      sendAndPrint.provideCustomLayer(backendLayer).exitCode
  }
}
