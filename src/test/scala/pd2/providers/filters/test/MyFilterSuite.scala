package pd2.providers.filters.test

import pd2.config.test.ConfigMock
import pd2.data.DatabaseService
import pd2.data.test.{TestBackend, TestDatabaseService}
import pd2.providers.TrackDto
import pd2.providers.filters.test.OnlyNewFilterSuite.{suite, testM}
import zio.ZIO
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion.{anything, equalTo, isFalse, isTrue}
import zio.test._
import zio.test.DefaultRunnableSpec
import zio.test.mock.Expectation.value

import java.time.{Duration, LocalDate, LocalDateTime}

object MyFilterSuite extends DefaultRunnableSpec {

  override def spec =
    suite("MyFilterSuite")(

      testM("Filtering by artist1") {

        val configMock =
          ConfigMock.MyArtists(value(List("Jay J"))) ++
          ConfigMock.MyLabels(value(List("Defected")))

        val trackDto = TrackDto(
          "Jay J, Artist2",
          "123",
          "123",
          "123",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val test = for {
          actual    <- pd2.providers.filters.my.check(trackDto)
        } yield assert(actual)(isTrue)

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Filtering by artist2") {

        val configMock =
          ConfigMock.MyArtists(value(List("Jay J"))) ++
            ConfigMock.MyLabels(value(List("Defected")))

        val trackDto = TrackDto(
          "Jay J & Artist2",
          "123",
          "123",
          "123",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val test = for {
          actual    <- pd2.providers.filters.my.check(trackDto)
        } yield assert(actual)(isTrue)

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Filtering by artist3") {

        val configMock =
          ConfigMock.MyArtists(value(List("Jay J"))) ++
            ConfigMock.MyLabels(value(List("Defected")))

        val trackDto = TrackDto(
          "Artist2 feat. Jay J",
          "123",
          "123",
          "123",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val test = for {
          actual    <- pd2.providers.filters.my.check(trackDto)
        } yield assert(actual)(isTrue)

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Filtering by title") {

        val configMock =
          ConfigMock.MyArtists(value(List("Jay J"))) ++
            ConfigMock.MyLabels(value(List("Defected")))

        val trackDto = TrackDto(
          "123",
          "Face to Face (Jay J Mix)",
          "123",
          "123",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val test = for {
          actual    <- pd2.providers.filters.my.check(trackDto)
        } yield assert(actual)(isTrue)

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Whole word by artist") {

        val configMock =
          ConfigMock.MyArtists(value(List("Jay J"))) ++
          ConfigMock.MyLabels(value(List("Defected")))

        val trackDto = TrackDto(
          "Jay Jayson, Artist2",
          "123",
          "123",
          "123",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val test = for {
          actual    <- pd2.providers.filters.my.check(trackDto)
        } yield assert(actual)(isFalse)

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },

      testM("Whole word by title") {

        val configMock =
          ConfigMock.MyArtists(value(List("Jay J"))) ++
            ConfigMock.MyLabels(value(List("Defected")))

        val trackDto = TrackDto(
          "123",
          "Face to Face (Jay Jayson Mix)",
          "123",
          "123",
          LocalDate.parse("2003-05-26"),
          Duration.ofMinutes(5),
          "03-traxsource-house-featured",
          12345)

        val test = for {
          actual    <- pd2.providers.filters.my.check(trackDto)
        } yield assert(actual)(isFalse)

        test.provideCustomLayer(
          TestBackend.makeLayer >>> TestDatabaseService.makeLayer ++ configMock ++ Slf4jLogger.make((_, s) => s))
      },
    )
}
