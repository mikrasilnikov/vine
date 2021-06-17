package vine.data.test

import zio.test._
import zio.test.Assertion._
import vine.data.{TrackParsing => TP}
import vine.data.TrackParsing._

object TrackSuite extends DefaultRunnableSpec {

  def spec = {
    suite ("Track Suite") (

      suite("Artist parsing") (

        test("Tommy Largo") {
          val result = TP.parseArtists("Tommy Largo")
          val expected = List[Artist](Single("Tommy Largo"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("Demuir, Tommy Largo") {
          val result = TP.parseArtists("Demuir, Tommy Largo")

          val expected = List[Artist](
            Single("Demuir"),
            Single("Tommy Largo"))

          assert(result)(isSome(equalTo(expected)))
        },

        test("Damian Lazarus & The Ancient Moons") {
          val result = TP.parseArtists("Damian Lazarus & The Ancient Moons")

          val expected = List[Artist](
            Coop(Single("Damian Lazarus"), Single("The Ancient Moons")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("Damian Lazarus, Damian Lazarus & The Ancient Moons") {
          val result = TP.parseArtists("Damian Lazarus, Damian Lazarus & The Ancient Moons")

          val expected = List[Artist](
            Single("Damian Lazarus"),
            Coop(Single("Damian Lazarus"), Single("The Ancient Moons")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("Groove Junkies, Scott K., Indeya, Groove Junkies & Scott K.") {
          val result = TP.parseArtists("Groove Junkies, Scott K., Indeya, Groove Junkies & Scott K.")

          val expected = List[Artist](
            Single("Groove Junkies"),
            Single("Scott K."),
            Single("Indeya"),
            Coop(Single("Groove Junkies"), Single("Scott K.")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("A & B & C") {
          val result = TP.parseArtists(
            "A & B & C")

          val expected = List[Artist](
            Coop(
              Coop(
                Single("A"),
                Single("B")),
              Single("C")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("A feat B feat C") {
          val result = TP.parseArtists("A feat B feat C")

          val expected = List[Artist] (
            Feat(Feat(Single("A"), Single("B")), Single("C")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("A pres B pres C") {
          val result = TP.parseArtists("A pres B pres C")

          val expected = List[Artist] (
            Pres(Pres(Single("A"), Single("B")), Single("C")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("Rudimental & The Martinez Brothers feat. Donna Missal, Rudimental, The Martinez Brothers, Donna Missal") {
          val result = TP.parseArtists(
            "Rudimental & The Martinez Brothers feat. Donna Missal, Rudimental, The Martinez Brothers, Donna Missal")

          val expected = List[Artist](
            Feat(
                Coop(
                  Single("Rudimental"),
                  Single("The Martinez Brothers")),
              Single("Donna Missal")),
            Single("Rudimental"),
            Single("The Martinez Brothers"),
            Single("Donna Missal"))

          assert(result)(isSome(equalTo(expected)))
        },

        test("Justin Robertson Presents Revtone") {
          val result = TP.parseArtists(
            "Justin Robertson Presents Revtone")

          val expected = List[Artist](
            Pres(
              Single("Justin Robertson"),
              Single("Revtone")))

          assert(result)(isSome(equalTo(expected)))
        },

        test("Downtown pres Annette Bowen feat. Nikki_O") {
          val result = TP.parseArtists(
            "Downtown pres Annette Bowen feat. Nikki_O")

          val expected = List[Artist](
            Pres(
              Single("Downtown"),
              Feat(
                Single("Annette Bowen"),
                Single("Nikki_O"))))

          assert(result)(isSome(equalTo(expected)))
        }
      ),

      suite("Title parsing") (

        test("On Dat High") {
          val result = TP.parseTitle("On Dat High")
          val expected = Title("On Dat High", None, None)
          assert(result)(isSome(equalTo(expected)))
        },

        test("On Dat High (Original Mix)") {
          val result = TP.parseTitle("On Dat High (Original Mix)")
          val expected = Title("On Dat High", None, Some("(Original Mix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("On Dat High feat. Tommy Largo") {
          val result = TP.parseTitle("On Dat High feat. Tommy Largo")
          val expectedArists = Single("Tommy Largo") :: Nil
          val expected = Title("On Dat High", Some(expectedArists), None)
          assert(result)(isSome(equalTo(expected)))
        },

        test("On Dat High (feat. Tommy Largo)") {
          val result = TP.parseTitle("On Dat High (feat. Tommy Largo)")
          val expectedArists = Single("Tommy Largo") :: Nil
          val expected = Title("On Dat High", Some(expectedArists), None)
          assert(result)(isSome(equalTo(expected)))
        },

        test("On Dat High feat. Tommy Largo (Original Mix)") {
          val result = TP.parseTitle("On Dat High feat. Tommy Largo (Original Mix)")
          val expectedArists = Single("Tommy Largo") :: Nil
          val expected = Title("On Dat High", Some(expectedArists), Some("(Original Mix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("On Dat High (feat. Tommy Largo) (Original Mix)") {
          val result = TP.parseTitle("On Dat High (feat. Tommy Largo) (Original Mix)")
          val expectedArists = Single("Tommy Largo") :: Nil
          val expected = Title("On Dat High", Some(expectedArists), Some("(Original Mix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("Vibesin' (feat. OneDa & Trigga) (Original Mix)") {
          val result = TP.parseTitle("Vibesin' (feat. OneDa & Trigga) (Original Mix)")
          val expectedArists = Coop(Single("OneDa"), Single("Trigga")) :: Nil
          val expected = Title("Vibesin'", Some(expectedArists), Some("(Original Mix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("All My Life (feat. Andrea Martin, Sean Declase) (Mark Brickman Extended Remix)") {
          val result = TP.parseTitle("All My Life (feat. Andrea Martin, Sean Declase) (Mark Brickman Extended Remix)")
          val featuredArtist = Single("Andrea Martin") :: Single("Sean Declase") :: Nil
          val expected = Title("All My Life", Some(featuredArtist), Some("(Mark Brickman Extended Remix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("Lensor Express (Original Mix)") {
          val result = TP.parseTitle("Lensor Express (Original Mix)")
          val expected = Title("Lensor Express", None, Some("(Original Mix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("Bongos & Tambourines (Simple Symmetry Remix)") {
          val result = TP.parseTitle("Bongos & Tambourines (Simple Symmetry Remix)")
          val expected = Title("Bongos & Tambourines", None, Some("(Simple Symmetry Remix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("A (Remix)") {
          val result = TP.parseTitle("A (Remix)")
          val expected = Title("A", None, Some("(Remix)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("Unicode (Original Dub)") {
          val result = TP.parseTitle("We're Funkin' ♫♫♫ !!! (Original Dub)")
          val expected = Title("We're Funkin' ♫♫♫ !!!", None, Some("(Original Dub)"))
          assert(result)(isSome(equalTo(expected)))
        },

        test("Original mix removal 1") {
          val artist = TP.parseArtists("Artist")
          val title = TP.parseTitle("Title (Original Mix)")
          val expected = "Artist - Title"
          val actual = TP.rewriteTrackName(artist.get, title.get)
          assert(actual)(equalTo(expected))
        },

        test("Original mix removal 2") {
          val artist = TP.parseArtists("Artist")
          val title = TP.parseTitle("Title (Original)")
          val expected = "Artist - Title"
          val actual = TP.rewriteTrackName(artist.get, title.get)
          assert(actual)(equalTo(expected))
        },
      )
    )
  }
}
