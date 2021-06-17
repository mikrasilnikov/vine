package vine.data.test

import vine.config.test.ConfigMock
import vine.data.DatabaseService
import vine.providers.TrackDto
import zio._
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion._
import zio.test._
import zio.test.mock.Expectation.value

import java.time.{Duration, LocalDate, LocalDateTime}

object TrackTestDataBuilderSuite extends DefaultRunnableSpec {

  override def spec =
    suite("TrackTestDataBuilderSuite")(
      testM("Id is correctly set after insert") {

        val trackBuilder1 = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))

        val trackBuilder2 = TrackTestDataBuilder.empty
          .withArtist("David Penn, Ramona Renea")
          .withTitle("Lift Your Hands Up (Extended Mix)")
          .withLabel("Urbana")
          .withReleaseDate(LocalDate.parse("2010-08-15"))


        val test = for {
          _         <- trackBuilder1.build
          _         <- trackBuilder2.build
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db =>
                          import db.profile.api._
                          db.run(db.tracks.result)
                        }
        } yield
            assert(dbTracks.size)(equalTo(2)) &&
            assert(dbTracks(0).id)(equalTo(1)) &&
            assert(dbTracks(1).id)(equalTo(2))

        test.provideCustomLayer(TestBackend.makeLayer >>> TestDatabaseService.makeLayer)
      })
}
