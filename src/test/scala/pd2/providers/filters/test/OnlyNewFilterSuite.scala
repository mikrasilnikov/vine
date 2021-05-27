package pd2.providers.filters.test

import pd2.data.{DatabaseService, Track}
import pd2.data.test.{TestBackend, TestDatabaseService, TrackTestDataBuilder}
import zio.ZIO
import zio.console.putStrLn
import zio.test.Assertion.{anything, equalTo, fails, isFailure}
import zio.test.DefaultRunnableSpec
import zio.test.{DefaultRunnableSpec, assert}

import java.time.LocalDate

object OnlyNewFilterSuite extends DefaultRunnableSpec {
  override def spec =
    suite("TraxsourceDataProviderSuite")(

      suite("Parsing")(
        testM("!") {

          val trackInDb = Track("Artist", "Title", "Artist - Title", None, None, None, None)

          val testDbLayer = TestDatabaseService.makeLayer { db =>
            import db.profile.api._
            db.tracks.schema.create
          }

          val trackBuilder = TrackTestDataBuilder.empty
            .withArtist("Sandy Rivera")
            .withTitle("I Can't Stop")
            .withLabel("Underwater")
            .withReleaseDate(LocalDate.parse("2003-05-26"))

          val reader = for {
            expected  <- trackBuilder.build
            _         <- putStrLn(expected.toString)
            actual    <- ZIO.service[DatabaseService].flatMap { db =>
                          import db.profile.api._
                          db.run(db.tracks.take(1).result).map(_.head)
                        }
          } yield assert(actual)(equalTo(expected))

          reader.provideCustomLayer(TestBackend.makeLayer >>> testDbLayer)
        }
      )
    )
}
