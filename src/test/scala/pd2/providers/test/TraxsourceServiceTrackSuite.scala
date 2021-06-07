package pd2.providers.test

import pd2.providers.traxsource.TraxsourceServiceTrack.{TraxsourceServiceArtist, TraxsourceServiceLabel}
import pd2.providers.traxsource.TraxsourceServiceTrack
import sttp.model.Uri
import zio.ZIO
import zio.console.putStrLn
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assert}

import java.time.{Duration, LocalDate}

object TraxsourceServiceTrackSuite extends DefaultRunnableSpec with ManagedTestResources {
  override def spec =
    suite("TraxsourceDataProviderSuite")(

      suite("Parsing")(

        testM("Traxsource_ServiceResponse.xml") {
          val expected = TraxsourceServiceTrack(
            "03-traxsource-soulful-all",
            8803989,
            List(TraxsourceServiceArtist(92248, 1, "Hannah Wants", "hannah-wants")),
            "Dot Com",
            "/title/1589138/body-music-vol-1",
            "/track/8803989/dot-com",
            TraxsourceServiceLabel(9303, "iCompilations", "icompilations"),
            "House",
            "ITC2DI399B",
            Duration.ofSeconds(333),
            LocalDate.of(2018, 1, 12),
            Uri.parse("https://geo-static.traxsource.com/files/images/6e91e80f5f8438dbc3cf494de276497b.jpg").right.get,
            Uri.parse("https://geo-preview.traxsource.com/files/previews/9303/914d5e510aa1c7eda4b994411a17f5b5.mp3?ps=120").right.get,
            "G#min"
          )

          loadTextFileManaged("/Traxsource_ServiceResponse.xml.zip")
            .use { doc => ZIO.fromEither(TraxsourceServiceTrack.fromServiceResponse(doc, "03-traxsource-soulful-all"))
              .tapError(e => putStrLn(e.toString)) }
            .map(res => assert(res)(equalTo(List(expected))))
        },

        testM("Traxsource_ServiceResponse_WithRemixer.xml") {
          loadTextFileManaged("/Traxsource_ServiceResponse_WithRemixer.xml.zip")
            .use { doc =>
              ZIO.fromEither(TraxsourceServiceTrack.fromServiceResponse(doc, "03-traxsource-soulful-all"))
                .tapError(e => putStrLn(e.toString))
            }
            .map(res => assert(res.head.artist)(equalTo("Ronnie Herel, Gary Poole, Tasha LaRae")))
        },

        testM("Traxsource_ServiceResponse_DoubleQuotes.xml") {
          loadTextFileManaged("/Traxsource_ServiceResponse_DoubleQuotes.xml.zip")
            .use { doc =>
              ZIO.fromEither(TraxsourceServiceTrack.fromServiceResponse(doc, "03-traxsource-house-all"))
                .tapError(e => putStrLn(e.toString))
            }
            .map(res => assert(res.head.catNumber)(equalTo("884385733490")))
        },

        testM("Traxsource_ServiceResponse_Tabs.xml") {
          loadTextFileManaged("/Traxsource_ServiceResponse_Tabs.xml.zip")
            .use { doc =>
              ZIO.fromEither(TraxsourceServiceTrack.fromServiceResponse(doc, "03-traxsource-house-all"))
                .tapError(e => putStrLn(e.toString))
            }
            .map(res => assert(res.head.title)(equalTo("Hands Over Your Head      (Original)")))
        }


      )
    )
}
