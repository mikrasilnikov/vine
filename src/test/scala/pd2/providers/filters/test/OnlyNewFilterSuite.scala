package pd2.providers.filters.test

import pd2.config.test.ConfigMock
import pd2.data.DatabaseService
import pd2.data.test.{TestBackend, TestDatabaseService, TrackTestDataBuilder}
import pd2.providers.TrackDto
import zio.ZIO
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion.{equalTo, isFalse, isTrue}
import zio.test.mock.Expectation._
import zio.test.{DefaultRunnableSpec, assert}
import java.time.{Duration, LocalDate, LocalDateTime}

object OnlyNewFilterSuite extends DefaultRunnableSpec {

  override def spec =
    suite("OnlyNewFilterSuite")(

      testM("OnlyNewFilter.check - unknown track") {

          val trackDto = TrackDto(
            "Sandy Rivera",
            "I Can't Stop",
            "Underwater",
            "body music vol 1",
            LocalDate.parse("2003-05-26"),
            Duration.ofMinutes(5),
            "03-traxsource-house-featured",
            12345)

          val configMock = ConfigMock.RunId(value(LocalDateTime.now()))

          val test = for {
            actual    <- pd2.providers.filters.onlyNew.check(trackDto)
            count     <- ZIO.service[DatabaseService].flatMap { db =>
                          import db.profile.api._
                          db.run(db.tracks.size.result)
                        }
          } yield assert(actual)(isTrue) && assert(count)(equalTo(0))

          test.provideCustomLayer(
            TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
        },

      testM("OnlyNewFilter.check - existing completed") {

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val configMock = ConfigMock.RunId(value(LocalDateTime.now()))

        val test = for {
          _         <- trackBuilder.build
          actual    <- pd2.providers.filters.onlyNew.check(trackDto)
          count     <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.size.result)
          }
        } yield assert(actual)(isFalse) && assert(count)(equalTo(1))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("OnlyNewFilter.check - existing queued in previous run") {

        val previousRunId = LocalDateTime.parse("2020-01-01T00:00:00")
        val currentRunId  = previousRunId.plusHours(1)

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(previousRunId)

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          track0    <- trackBuilder.build
          result    <- pd2.providers.filters.onlyNew.check(trackDto)
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db => import db.profile.api._
                        db.run(db.tracks.result)
                      }
        } yield
          assert(result)(isTrue) &&
          assert(dbTracks.size)(equalTo(1)) &&
          assert(track0)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("OnlyNewFilter.check - existing queued in same run") {

        val previousRunId = LocalDateTime.parse("2020-01-01T00:00:00")
        val currentRunId  = previousRunId.plusHours(1)

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(currentRunId)

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          track0    <- trackBuilder.build
          result    <- pd2.providers.filters.onlyNew.check(trackDto)
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db => import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(result)(isFalse) &&
            assert(dbTracks.size)(equalTo(1)) &&
            assert(track0)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },


      testM("OnlyNewFilter.checkBeforeProcessing - unknownTrack") {

        val currentRunId  = LocalDateTime.parse("2020-01-01T00:00:00")

        /*val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(currentRunId)*/

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val expectedDbTrack = trackDto.toDbTrack(Some(currentRunId)).get.copy(id = 1)

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          result    <- pd2.providers.filters.onlyNew.checkBeforeProcessing(trackDto)
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db => import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(result)(isTrue) &&
          assert(dbTracks.size)(equalTo(1)) &&
          assert(expectedDbTrack)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("OnlyNewFilter.checkBeforeProcessing - existing completed") {

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val configMock = ConfigMock.RunId(value(LocalDateTime.now()))

        val test = for {
          track0    <- trackBuilder.build
          actual    <- pd2.providers.filters.onlyNew.checkBeforeProcessing(trackDto)
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db => import db.profile.api._
                          db.run(db.tracks.result)
          }
        } yield
            assert(actual)(isFalse) &&
            assert(dbTracks.size)(equalTo(1)) &&
            assert(dbTracks.head)(equalTo(track0))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("OnlyNewFilter.checkBeforeProcessing - existing queued in previous run") {

        val currentRunId = LocalDateTime.parse("2020-01-01T00:00:00")
        val previousRunId  = currentRunId.minusHours(1)

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(previousRunId)

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          track0    <- trackBuilder.build
          result    <- pd2.providers.filters.onlyNew.checkBeforeProcessing(trackDto)
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db => import db.profile.api._
                          db.run(db.tracks.result)
                        }
          expectedTrack = track0.copy(queued = Some(currentRunId))
        } yield
            assert(result)(isTrue) &&
            assert(dbTracks.size)(equalTo(1)) &&
            assert(expectedTrack)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("OnlyNewFilter.checkBeforeProcessing - existing queued in same run") {

        val currentRunId = LocalDateTime.parse("2020-01-01T00:00:00")
        val previousRunId  = currentRunId.minusHours(1)

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(currentRunId)

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          track0    <- trackBuilder.build
          result    <- pd2.providers.filters.onlyNew.checkBeforeProcessing(trackDto)
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db => import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
            assert(result)(isFalse) &&
            assert(dbTracks.size)(equalTo(1)) &&
            assert(track0)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },
    )
}
