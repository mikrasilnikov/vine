package pd2.data

import cats.data.NonEmptyList
import cats.parse.Parser.not
import cats.parse.Rfc5234.{char, sp}
import cats.parse.{Parser => P}

import java.time.LocalDate

case class Track(artist: String, title: String, label: String, releaseDate: LocalDate, feed: String, id: Int = 0)

  // Artist1 presents Artist2 - Title         -> Artist1, Artist2 - Title
  // Artist1 pres. Artist2 - Title            -> Artist1, Artist2 - Title
  // Artist - Title (Original Mix)            -> Artist - Title
  // Demuir, Tommy Largo - On Dat High (feat. Tommy Largo)
  // Demuir, Tommy Largo - On Dat High (feat. Tommy Largo) (Original Mix)
  // Cristian Ferretti, Rik Spin - Music Should Not Stop feat. Omar Daini (Original Mix)
  // Damian Lazarus, Damian Lazarus & The Ancient Moons - Fly Away (Jonas Rathsman Remix)
  // Groove Junkies, Scott K., Indeya, Groove Junkies & Scott K. - Higher feat. Indeya (Original Mix)
  // Jerome Robins - Groovejet (If This Ain't Love) (Earth n Days Remix)
  // Louie Vega - Love Having You Around Feat. Rochelle Fleming & Barbara Tucker (Louie Vega Main Mix)
  // Rudimental & The Martinez Brothers feat. Donna Missal, Rudimental, The Martinez Brothers, Donna Missal - No Fear
  // Zed Bias, Trigga, OneDa - Vibesin' (feat. OneDa & Trigga) (Original Mix)
  // Henry Was	Everybody Thinks (that everybody...) (Original Mix)
  // Justin Robertson Presents Revtone - The Brightest Thing (Full Length Mix)
  // Robert Bond, Paris Brightledge - For Love featuring Robert Bond (Eric Kupper Remix)
  // Glaubitz & Roc, Sudad G. - Sunshine Day === Glaubitz, Roc, Sudad G. - Sunshine Day
  // L'Tric, Andrea Martin, Sean Declase - All My Life (feat. Andrea Martin, Sean Declase) (Mark Brickman Extended Remix)
  // Honey Dijon, Nikki_O, Annette Bowen - Downtown feat. Annette Bowen feat. Nikki_O (Louie Vega Extended Raw Dub Mix)
/*
  val optimizedName: String = {
    ???
  }*/


object Track {
  def tupled = (Track.apply _).tupled

  //<editor-fold desc="Artist parser">

  trait Artist
  case class Single(name : String) extends Artist
  case class Coop(a1 : Artist, a2 : Artist) extends Artist
  case class Pres(a1 : Artist, a2 : Artist) extends Artist
  case class Feat(a1 : Artist, a2 : Artist) extends Artist

  val pairSep = sp.rep.?.soft ~ P.char(',') ~ sp.rep.?
  val coopSep = sp.rep.?.soft ~ P.char('&') ~ sp.rep.?

  val pres = P.oneOf(List(P.ignoreCase("presents"), P.ignoreCase("pres."), P.ignoreCase("pres")))
  val presSep = sp.rep.?.soft ~ pres ~ sp.rep.?

  val feat = P.oneOf(List(P.ignoreCase("featuring"), P.ignoreCase("feat."), P.ignoreCase("feat")))
  val featSep = sp.rep.?.soft ~ feat ~ sp.rep.?

  val anySep = presSep.backtrack | featSep.backtrack | pairSep.backtrack | coopSep

  val notSp = P.charWhere(c => !Character.isSpaceChar(c))

  val single = (notSp ~ (char.soft <* not(anySep)).rep0 ~ char.?).string.map(_.trim).map(Single)

  val artistP = P.recursive[Artist] { recurse =>
    val coopP = ((single <* coopSep) ~ recurse).map(Coop.tupled)
    val presP = ((single <* presSep) ~ recurse).map(Pres.tupled)
    val featP = ((single <* featSep) ~ recurse).map(Feat.tupled)

    (presP.backtrack | featP.backtrack | coopP.backtrack | single)
      .asInstanceOf[P[Artist]]
  }

  val artistsP = artistP.repSep(pairSep)

  private def rotateLeft(artist : Artist) : Artist = artist match {

    case Coop(a0, Feat(a1, a2)) => Feat(Coop(a0, a1), rotateLeft(a2)) // (A & B) feat C
    case Coop(a0, Pres(a1, a2)) => Pres(Coop(a0, a1), rotateLeft(a2)) // (A & B) pres C
    case Coop(a0, Coop(a1, a2)) => Coop(Coop(a0, a1), rotateLeft(a2))
    case Coop(a0, that) => Coop(a0, rotateLeft(that))

    case Feat(a0, Feat(a1, a2)) => Feat(Feat(a0, a1), rotateLeft(a2))
    case Feat(a0, that) => Feat(a0, rotateLeft(that))

    case Pres(a0, Pres(a1, a2)) => Pres(Pres(a0, a1), rotateLeft(a2))
    case Pres(a0, that) => Pres(a0, rotateLeft(that))

    case _ => artist
  }

  //</editor-fold>

  def parseArtists(input : String) : Option[List[Artist]] =
    artistsP.parse(input) match {
      case Right((_, result)) => Some(result.toList.map(rotateLeft))
      case _ => None
    }

}

