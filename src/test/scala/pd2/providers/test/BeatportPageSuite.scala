package pd2.providers.test


import java.time.LocalDate
import pd2.providers.Pager
import pd2.providers.beatport.{BeatportPage, BeatportPageArtist, BeatportPageTrack}
import sttp.model.Uri
import zio.ZIO
import zio.test._
import zio.test.Assertion.{anything, equalTo, isNone, isSome}
import zio.test.DefaultRunnableSpec

import java.time.Duration

//noinspection SpellCheckingInspection
object BeatportPageSuite extends DefaultRunnableSpec with ManagedTestResources {

  override def spec =
    suite("BeatportPageSuite")(

      testM("Beatport_Top100.html") {

        val expectedFirstTrack = BeatportPageTrack(
          id = 15206183,
          artists = List(BeatportPageArtist(42511, "Piero Pirupa", "piero-pirupa")),
          title = "",
          name = "Everybody's Free (To Feel Good)",
          mix = "Deeper Purpose Extended Remix",
          release = "Everybody's Free (To Feel Good) [Deeper Purpose Extended Remix]",
          releaseDate = LocalDate.parse("2021-05-14"),
          label = "SPINNIN' DEEP",
          duration = Some(Duration.ofMillis(366724)),
          previewUrl = "https://geo-samples.beatport.com/track/53db3f90-ba5a-4b0b-aa0d-be58c13a9fb9.LOFI.mp3",
          key = Some("A min")
        )

        val expectedSeventhTrack = BeatportPageTrack(
          id = 15166533,
          artists = List(BeatportPageArtist(419524, "Kideko", "kideko"), BeatportPageArtist(648493, "Saffron Stone", "saffron-stone")),
          title = "",
          name = "The Music",
          mix = "Extended Mix",
          release = "The Music",
          releaseDate = LocalDate.parse("2021-04-30"),
          label = "Armada Subjekt",
          duration = Some(Duration.ofMillis(330254)),
          previewUrl = "https://geo-samples.beatport.com/track/a1cee24e-c80e-4992-bc80-f6ef83504785.LOFI.mp3",
          key = Some("D min")
        )

        loadTextFileManaged("/Beatport_Top100.html.zip")
          .use { doc => ZIO.fromEither(BeatportPage.parse(doc)) }
          .map { res =>
            assert(res.pager)(isNone) &&
            assert(res.tracks.length)(equalTo(100)) &&
            assert(res.tracks.head)(equalTo(expectedFirstTrack)) &&
            assert(res.tracks.drop(6).head)(equalTo(expectedSeventhTrack))
          }
      },

      testM("Beatport_Tracks_FirstPage.html") {

        val expectedFirstTrack = BeatportPageTrack(
          id = 14806028,
          artists = List(BeatportPageArtist(696433, "Rocho Sainkt", "rocho-sainkt")),
          title = "",
          name = "Arzukela",
          mix = "House Mix",
          release = "House Ocean",
          releaseDate = LocalDate.parse("2021-05-01"),
          label = "Berry Parfait",
          duration = Some(Duration.ofMillis(270094)),
          previewUrl = "https://geo-samples.beatport.com/track/1337b5e7-db28-4b7e-b29c-102d367731de.LOFI.mp3",
          key = Some("A min")
        )

        val expectedLastTrack = BeatportPageTrack(
          id = 14806043,
          artists = List(BeatportPageArtist(679217, "Dyba", "dyba"), BeatportPageArtist(696442, "Sound Expander", "sound-expander")),
          title = "",
          name = "Technically Speaking feat. Sound Expander",
          mix = "Original Mix",
          release = "House Ocean",
          releaseDate = LocalDate.parse("2021-05-01"),
          label = "Berry Parfait",
          duration = Some(Duration.ofMillis(191219)),
          previewUrl = "https://geo-samples.beatport.com/track/a3955c7d-c2c4-46f6-b20c-99c1de2f5541.LOFI.mp3",
          key = Some("D min")
        )

        loadTextFileManaged("/Beatport_Tracks_FirstPage.html.zip")
          .use { doc => ZIO.fromEither(BeatportPage.parse(doc)) }
          .map { res =>
            assert(res.pager)(isSome(equalTo(Pager(1, 66)))) &&
              assert(res.tracks.length)(equalTo(25)) &&
              assert(res.tracks.head)(equalTo(expectedFirstTrack)) &&
              assert(res.tracks.drop(24).head)(equalTo(expectedLastTrack))
          }
      },

      testM("Beatport_Tracks_MiddlePage.html") {

        loadTextFileManaged("/Beatport_Tracks_MiddlePage.html.zip")
          .use { doc => ZIO.fromEither(BeatportPage.parse(doc)) }
          .map { res =>
            assert(res.pager)(isSome(equalTo(Pager(30, 66)))) &&
              assert(res.tracks.length)(equalTo(25)) &&
              assert(res.tracks.head.id)(equalTo(15129444)) &&
              assert(res.tracks(24).id)(equalTo(15133827)) &&
              assert(res.tracks(18).duration)(isNone) &&
              assert(res.tracks(18).key)(isNone)
          }
      },

      testM("Beatport_Tracks_MiddlePage.html") {

        loadTextFileManaged("/Beatport_Tracks_LastPage.html.zip")
          .use { doc => ZIO.fromEither(BeatportPage.parse(doc)) }
          .map { res =>
            assert(res.pager)(isSome(equalTo(Pager(66, 66)))) &&
              assert(res.tracks.length)(equalTo(12))
          }
      }
    )
}
