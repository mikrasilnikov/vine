package pd2.data.test

import zio.test._
import zio.test.Assertion._
import pd2.data.Track
import pd2.data.Track._

object TrackSuite extends DefaultRunnableSpec {
  def spec =
    suite("Artist parsing")(

      test("Tommy Largo") {
        val result = Track.parseArtists("Tommy Largo")
        val expected = List[Artist](Single("Tommy Largo"))
        assert(result)(isSome(equalTo(expected)))
      },

      test("Demuir, Tommy Largo") {
        val result = Track.parseArtists("Demuir, Tommy Largo")

        val expected = List[Artist](
          Single("Demuir"),
          Single("Tommy Largo"))

        assert(result)(isSome(equalTo(expected)))
      },

      test("Damian Lazarus & The Ancient Moons") {
        val result = Track.parseArtists("Damian Lazarus & The Ancient Moons")

        val expected = List[Artist](
          Coop(Single("Damian Lazarus"), Single("The Ancient Moons")))

        assert(result)(isSome(equalTo(expected)))
      },

      test("Damian Lazarus, Damian Lazarus & The Ancient Moons") {
        val result = Track.parseArtists("Damian Lazarus, Damian Lazarus & The Ancient Moons")

        val expected = List[Artist](
          Single("Damian Lazarus"),
          Coop(Single("Damian Lazarus"), Single("The Ancient Moons")))

        assert(result)(isSome(equalTo(expected)))
      },

      test("Groove Junkies, Scott K., Indeya, Groove Junkies & Scott K.") {
        val result = Track.parseArtists("Groove Junkies, Scott K., Indeya, Groove Junkies & Scott K.")

        val expected = List[Artist](
          Single("Groove Junkies"),
          Single("Scott K."),
          Single("Indeya"),
          Coop(Single("Groove Junkies"), Single("Scott K.")))

        assert(result)(isSome(equalTo(expected)))
      },

      test("A & B & C") {
        val result = Track.parseArtists(
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
        val result = Track.parseArtists("A feat B feat C")

        val expected = List[Artist] (
          Feat(Feat(Single("A"), Single("B")), Single("C")))

        assert(result)(isSome(equalTo(expected)))
      },

      test("Rudimental & The Martinez Brothers feat. Donna Missal, Rudimental, The Martinez Brothers, Donna Missal") {
        val result = Track.parseArtists(
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
        val result = Track.parseArtists(
          "Justin Robertson Presents Revtone")

        val expected = List[Artist](
          Pres(
            Single("Justin Robertson"),
            Single("Revtone")))

        assert(result)(isSome(equalTo(expected)))
      },

      test("Downtown pres Annette Bowen feat. Nikki_O") {
        val result = Track.parseArtists(
          "Downtown pres Annette Bowen feat. Nikki_O")

        val expected = List[Artist](
          Pres(
            Single("Downtown"),
            Feat(
              Single("Annette Bowen"),
              Single("Nikki_O"))))

        assert(result)(isSome(equalTo(expected)))
      }
    )
}
