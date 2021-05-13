package pd2.web.test

import pd2.web.TraxsourceServiceTrack
import pd2.web.TraxsourceServiceTrack.{TraxsourceServiceArtist, TraxsourceServiceLabel}
import zio.ZIO
import zio.console.putStrLn
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

import java.time.LocalDate

object TraxsourceServiceTrackSuite extends DefaultRunnableSpec with ManagedTestResources {
  override def spec =
    suite("TraxsourceDataProviderSuite")(

      suite("Parsing")(

        testM("Traxsource_ServiceResponse.xml") {
          val expected = TraxsourceServiceTrack(
            8803989,
            List(TraxsourceServiceArtist(92248, 1, "Hannah Wants", "hannah-wants")),
            "Dot Com",
            "/title/1589138/body-music-vol-1",
            "/track/8803989/dot-com",
            TraxsourceServiceLabel(9303, "iCompilations", "icompilations"),
            "House",
            "ITC2DI399B",
            333,
            LocalDate.of(2018, 1, 12),
            "https://geo-static.traxsource.com/files/images/6e91e80f5f8438dbc3cf494de276497b.jpg",
            "https://geo-preview.traxsource.com/files/previews/9303/914d5e510aa1c7eda4b994411a17f5b5.mp3?ps=120",
            "G#min"
          )

          loadTextFileManaged("/Traxsource_ServiceResponse.xml")
            .use { doc => ZIO.fromEither(TraxsourceServiceTrack.fromServiceResponse(doc))
              .tapError(e => putStrLn(s"${e.msg}\n${e.parserInput}")) }
            .map(res => assert(res)(equalTo(List(expected))))
        }
      )
    )
}