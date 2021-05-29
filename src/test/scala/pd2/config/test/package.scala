package pd2.config

import zio.nio.core.file.Path
import zio.test.mock
import zio.{Has, Semaphore, URLayer, ZLayer}
import zio.test.mock.{Mock, mockable}

import java.time.LocalDateTime

package object test {

  object ConfigMock extends Mock[Config] {

    object ConfigDescription extends Method[Unit, Nothing, ConfigDescription]
    object MyArtists extends Method[Unit, Nothing, List[String]]
    object MyLabels extends Method[Unit, Nothing, List[String]]
    object ShitLabels extends Method[Unit, Nothing, List[String]]
    object TargetPath extends Method[Unit, Nothing, Path]
    object RunId extends Method[Unit, Nothing, LocalDateTime]
    object GlobalConnSemaphore extends Method[Unit, Nothing, Semaphore]

    val compose: URLayer[Has[mock.Proxy], Config] =
      ZLayer.fromServiceM { proxy =>
        withRuntime.map { rts =>
          new Config.Service {
            def configDescription: ConfigDescription = rts.unsafeRun(proxy(ConfigDescription))
            def myArtists: List[String] = rts.unsafeRun(proxy(MyArtists))
            def myLabels: List[String] = rts.unsafeRun(proxy(MyLabels))
            def shitLabels: List[String] = rts.unsafeRun(proxy(ShitLabels))
            def targetPath: Path = rts.unsafeRun(proxy(TargetPath))
            def runId: LocalDateTime = rts.unsafeRun(proxy(RunId))
            def globalConnSemaphore: Semaphore = rts.unsafeRun(proxy(GlobalConnSemaphore))
          }
        }
      }
  }
}
