package pd2.data

import cats.parse.{Parser => P, Parser0 => P0}
import cats.parse.Parser.{anyChar, not}
import cats.parse.Rfc5234.{char, sp}


object TrackParsing {

  private val mixLabels = List("mix", "remix", "dub", "rub", "instrumental", "original", "extended", "edit")
  private val mixLabelsSpacePrefixed = mixLabels.map(' ' + _)

  private val featLabels = List("featuring", "feat.", "feat", "ft.", "ft")

  sealed trait Artist
  case class Single(name : String) extends Artist
  case class Coop(a1 : Artist, a2 : Artist) extends Artist // A & B
  case class Pres(a1 : Artist, a2 : Artist) extends Artist // A presents B
  case class Feat(a1 : Artist, a2 : Artist) extends Artist // A feat B

  final case class Title(actualTitle: String, featuredArtist : Option[List[Artist]], mix: Option[String])

  // <editor-fold desc="Artist parser">

  val pairSep = sp.rep.?.soft ~ P.char(',') ~ sp.rep.?
  val coopSep = sp.rep.?.soft ~ P.char('&') ~ sp.rep.?

  val pres = P.oneOf(List(P.ignoreCase("presents"), P.ignoreCase("pres."), P.ignoreCase("pres")))
  val presSep = sp.rep.soft ~ pres ~ sp.rep

  val feat = P.oneOf(List(P.ignoreCase("featuring"), P.ignoreCase("feat."), P.ignoreCase("feat")))
  val featSep = sp.rep.soft ~ feat ~ sp.rep

  val anySep = presSep.backtrack | featSep.backtrack | pairSep.backtrack | coopSep

  val notSp = P.charWhere(c => !Character.isSpaceChar(c))

  //val single = (notSp ~ (char.soft <* not(anySep)).rep0 ~ char.?).string.map(_.trim).map(Single)
  val single = ((anyChar.soft <* not(P.end | anySep)).rep0.with1 ~ anyChar).string.map(Single)

  val artistP = P.recursive[Artist] { recurse =>
    val coopP = ((single <* coopSep) ~ recurse).map(Coop.tupled)
    val presP = ((single <* presSep) ~ recurse).map(Pres.tupled)
    val featP = ((single <* featSep) ~ recurse).map(Feat.tupled)

    (presP.backtrack | featP.backtrack | coopP.backtrack | single)
      .asInstanceOf[P[Artist]]
  }

  val artistsP = artistP.repSep(pairSep)

  // </editor-fold>

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

  /*
  Пытается найти индекс символа, с которого предположительно начинается название микса.
  Music Should Not Stop (Jonas Rathsman (PL) Remix)
                        ^
  Название микса включает в себя внешние скобки.
  */
  private def findLastParenthesesGroupStart(s : String) : Option[Int] = {
    if (s.last == ')') {
      def loop(pos : Int, depth : Int) : Option[Int] =
        s(pos) match {
          case _ if pos == 0 => None
          case '(' if depth == 1 => Some(pos)
          case ')' => loop(pos-1, depth+1)
          case '(' => loop(pos-1, depth-1)
          case _   => loop(pos-1, depth)
        }
      loop(s.length-1, 0)
    } else {
      None
    }
  }

  /*
    Downtown (Louie Vega Extended Raw Dub Mix) ->
      (
        "Downtown",
        Some("(Louie Vega Extended Raw Dub Mix)")
      )

    Downtown ->
      ("Downtown", None)
  */
  private def extractTitleAndMixName(s : String) : (String, Option[String]) = {
    val partsOption = for {
      start <- findLastParenthesesGroupStart(s)
      candidateLower = s.substring(start+1, s.length-1).toLowerCase
      _ <- mixLabelsSpacePrefixed.find(l => candidateLower.endsWith(l)) // (... ... Remix) или (Dub)
        .orElse(mixLabels.find(l => candidateLower == l))
      (title, mix) = s.splitAt(start)
    } yield (title.stripTrailing(), mix)

    partsOption match {
      case Some((title, mix)) => (title, Some(mix))
      case None => (s, None)
    }

  }

  /*
    All My Life (feat. Andrea Martin, Sean Declase) ->
      All My Life feat. Andrea Martin, Sean Declase
  */
  private def removeParenthesesAroundFeaturedBlock(s : String) : String = {
    val featBlockStart = for {
      start <- findLastParenthesesGroupStart(s)
      candidateLower = s.substring(start+1, s.length-1).toLowerCase
      _ <- featLabels.find(l => candidateLower.startsWith(l))
    } yield start

    featBlockStart.fold(s) { start =>
      s.substring(0, start) + s.substring(start+1, s.length-1)
    }
  }

  def parseArtists(s : String) : Option[List[Artist]] = artistsP.parse(s) match {
      case Right((_, result)) => Some(result.toList.map(rotateLeft))
      case _ => None
    }

  type ActualTitle = String
  type FeaturedArtist = Artist
  private def parseTitlePartUsingArtistsP(s : String) : Option[(ActualTitle, Option[List[FeaturedArtist]])] = {
    artistsP.parse(s) match {
      case Right((_, result)) => result.toList match {
        case Feat(Single(actualTitle), artist) :: tail => Some((actualTitle, Some(artist :: tail)))
        case _ => Some((s, None))
      }
      case _ => None
    }
  }

  def parseTitle(s : String) : Option[Title] = {
    val (beforeMix, mixOption) = extractTitleAndMixName(s)
    val parsedBeforeMix = (removeParenthesesAroundFeaturedBlock _ andThen parseTitlePartUsingArtistsP) (beforeMix)

    parsedBeforeMix match {
      case Some((actualTitle, featuredArtist)) => Some(Title(actualTitle, featuredArtist, mixOption))
      case None => None
    }
  }
}
