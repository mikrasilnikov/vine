package pd2.config

import zio.nio.core.file.Path
import zio.test.mock
import zio.{Has, Semaphore, URLayer, ZLayer}
import zio.test.mock.{Mock, mockable}

import java.time.{LocalDate, LocalDateTime}
import scala.util.matching.Regex

package object test {

  object ConfigMock extends Mock[Config] {

    object ConfigDescription extends Method[Unit, Nothing, ConfigDescription]
    object DateFrom extends Method[Unit, Nothing, LocalDate]
    object DateTo extends Method[Unit, Nothing, LocalDate]
    object MyArtistsRegexes extends Method[Unit, Nothing, List[Regex]]
    object MyLabels extends Method[Unit, Nothing, List[String]]
    object ShitLabels extends Method[Unit, Nothing, List[String]]
    object PreviewsBaseBath extends Method[Unit, Nothing, Path]
    object RunId extends Method[Unit, Nothing, LocalDateTime]
    object GlobalConnSemaphore extends Method[Unit, Nothing, Semaphore]
    object AppPath extends Method[Unit, Nothing, Path]
    object DownloadTracks extends Method[Unit, Nothing, Boolean]

    val compose: URLayer[Has[mock.Proxy], Config] =
      ZLayer.fromServiceM { proxy =>
        withRuntime.map { rts =>
          new Config.Service {
            def configDescription: ConfigDescription = rts.unsafeRun(proxy(ConfigDescription))
            def dateFrom : LocalDate = rts.unsafeRun(proxy(DateFrom))
            def dateTo : LocalDate = rts.unsafeRun(proxy(DateTo))
            def myArtistsRegexes: List[Regex] = rts.unsafeRun(proxy(MyArtistsRegexes))
            def myLabels: List[String] = rts.unsafeRun(proxy(MyLabels))
            def shitLabels: List[String] = rts.unsafeRun(proxy(ShitLabels))
            def previewsBasePath: Path = rts.unsafeRun(proxy(PreviewsBaseBath))
            def runId: LocalDateTime = rts.unsafeRun(proxy(RunId))
            def globalConnSemaphore: Semaphore = rts.unsafeRun(proxy(GlobalConnSemaphore))
            def appPath: Path = rts.unsafeRun(proxy(AppPath))
            def downloadTracks: Boolean = rts.unsafeRun(proxy(DownloadTracks))
          }
        }
      }
  }
}
