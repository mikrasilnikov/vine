package pd2.processing.test

import pd2.config.test.ConfigMock
import pd2.data.DatabaseService
import pd2.data.test._
import pd2.processing.Deduplication._
import pd2.providers.TrackDto
import zio._
import zio.test._
import zio.test.Assertion._
import zio.logging.slf4j.Slf4jLogger
import zio.test.mock.Expectation.value
import java.time._

object DeduplicationSuite extends DefaultRunnableSpec {

  override def spec =
    suite("DeduplicationSuite")(

      testM("Deduplication.deduplicateOrEnqueue - unknownTrack") {

        val currentRunId = LocalDateTime.parse("2020-01-01T00:00:00")

        val trackDto = TrackDto(
          "Sandy Rivera",
          "I Can't Stop",
          "Underwater",
          "body music vol 1",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345,
          "http://static.traxsource.com/qwerty.mp3")

        val expectedDbTrack = trackDto.toDbTrack(Some(currentRunId)).get.copy(id = 1)

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          result <- pd2.processing.Deduplication.deduplicateOrEnqueue(trackDto)
          dbTracks <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(dbTracks.size)(equalTo(1)) &&
          assert(expectedDbTrack)(equalTo(dbTracks.head)) &&
          assert(result)(equalTo(Enqueued(expectedDbTrack)))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Deduplication.deduplicateOrEnqueue - existing completed") {

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
          12345,
          "http://static.traxsource.com/qwerty.mp3")

        val configMock = ConfigMock.RunId(value(LocalDateTime.now()))

        val test = for {
          track0 <- trackBuilder.build
          actual <- pd2.processing.Deduplication.deduplicateOrEnqueue(trackDto)
          dbTracks <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(actual)(equalTo(Duplicate(track0))) &&
          assert(dbTracks.size)(equalTo(1)) &&
          assert(dbTracks.head)(equalTo(track0))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Deduplication.deduplicateOrEnqueue - existing queued in previous run") {

        val currentRunId = LocalDateTime.parse("2020-01-01T00:00:00")
        val previousRunId = currentRunId.minusHours(1)

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
          12345,
          "http://static.traxsource.com/qwerty.mp3")

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          track0 <- trackBuilder.build
          result <- pd2.processing.Deduplication.deduplicateOrEnqueue(trackDto)
          dbTracks <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
          expectedTrack = track0.copy(queued = Some(currentRunId))
        } yield
          assert(result)(equalTo(Resumed(dbTracks(0), previousRunId))) &&
          assert(dbTracks.size)(equalTo(1)) &&
          assert(expectedTrack)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Deduplication.deduplicateOrEnqueue - existing queued in same run") {

        val currentRunId = LocalDateTime.parse("2020-01-01T00:00:00")
        val previousRunId = currentRunId.minusHours(1)

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
          12345,
          "http://static.traxsource.com/qwerty.mp3")

        val configMock = ConfigMock.RunId(value(currentRunId))

        val test = for {
          track0 <- trackBuilder.build
          result <- pd2.processing.Deduplication.deduplicateOrEnqueue(trackDto)
          dbTracks <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(result)(equalTo(InProcess(track0))) &&
          assert(dbTracks.size)(equalTo(1)) &&
          assert(track0)(equalTo(dbTracks.head))

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Deduplication.markAsCompleted - Duplicate") {

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))

        val test = for {
          track0    <- trackBuilder.build
          _         <- pd2.processing.Deduplication.markAsCompleted(Duplicate(track0))
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db =>
                        import db.profile.api._
                        db.run(db.tracks.result)
                      }
        } yield
          assert(dbTracks.size)(equalTo(1)) &&
          assert(dbTracks.head)(equalTo(track0))

        test.provideCustomLayer(TestBackend.makeLayer >>> TestDatabaseService.makeLayer)
      },

      testM("Deduplication.markAsCompleted - InProcess") {

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(LocalDateTime.parse("2021-06-01T12:00:00"))

        val test = for {
          track0    <- trackBuilder.build
          _         <- pd2.processing.Deduplication.markAsCompleted(InProcess(track0))
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(dbTracks.size)(equalTo(1)) &&
          assert(dbTracks.head)(equalTo(track0))

        test.provideCustomLayer(TestBackend.makeLayer >>> TestDatabaseService.makeLayer)
      },

      testM("Deduplication.markAsCompleted - Enqueued") {

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(LocalDateTime.parse("2021-06-01T12:00:00"))

        val test = for {
          track0    <- trackBuilder.build
          _         <- pd2.processing.Deduplication.markAsCompleted(Enqueued(track0))
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(dbTracks.size)(equalTo(1)) &&
          assert(dbTracks.head)(equalTo(track0.copy(queued = None)))

        test.provideCustomLayer(TestBackend.makeLayer >>> TestDatabaseService.makeLayer)
      },

      testM("Deduplication.markAsCompleted - Resumed") {

        val trackBuilder = TrackTestDataBuilder.empty
          .withArtist("Sandy Rivera")
          .withTitle("I Can't Stop")
          .withLabel("Underwater")
          .withReleaseDate(LocalDate.parse("2003-05-26"))
          .withQueued(LocalDateTime.parse("2021-06-01T12:00:00"))

        val test = for {
          track0    <- trackBuilder.build
          _         <- pd2.processing.Deduplication.markAsCompleted(Enqueued(track0))
          dbTracks  <- ZIO.service[DatabaseService].flatMap { db =>
            import db.profile.api._
            db.run(db.tracks.result)
          }
        } yield
          assert(dbTracks.size)(equalTo(1)) &&
            assert(dbTracks.head)(equalTo(track0.copy(queued = None)))

        test.provideCustomLayer(TestBackend.makeLayer >>> TestDatabaseService.makeLayer)
      }
    )
}