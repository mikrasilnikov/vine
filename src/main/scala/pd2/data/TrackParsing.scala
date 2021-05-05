package pd2.data

import cats.parse.{Parser => P, Parser0 => P0}
import cats.parse.Parser.{anyChar, not}
import cats.parse.Rfc5234.{char, sp}

object TrackParsing {

  import Parser._

  sealed trait Artist
  case class Single(name : String) extends Artist
  case class Coop(a1 : Artist, a2 : Artist) extends Artist // A & B
  case class Pres(a1 : Artist, a2 : Artist) extends Artist // A presents B
  case class Feat(a1 : Artist, a2 : Artist) extends Artist // A feat B

  final case class Title(actualTitle: String, featuredArtist : Option[List[Artist]], mix: Option[String])

  def parseArtists(s : String) : Option[List[Artist]] = artistsP.parse(s) match {
    case Right((_, result)) => Some(result.toList.map(fixPriorities))
    case _ => None
  }

  def parseTitle(s : String) : Option[Title] = {
    val (title, mixOption) = extractTitleAndMixName(s)
    val parsedTitle = (removeParenthesesAroundFeaturedBlock _ andThen parseTitlePartUsingArtistsP) (title)

    parsedTitle match {
      case Some((actualTitle, featuredArtist)) => Some(Title(actualTitle, featuredArtist, mixOption))
      case None => None
    }
  }

  def rewriteTrackName(parsedArtists : List[Artist], parsedTitle : Title) : String = {

    def getAllNames(artist : Artist) : List[String] = artist match {
      case Single(name) => List(name)
      case Feat(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
      case Coop(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
      case Pres(a1, a2) => getAllNames(a1) ++ getAllNames(a2)
    }

    val artistNamesFromArtistFiled = parsedArtists.flatMap(a => getAllNames(a))

    val featuredArtistNamesO = for {
      aList <- parsedTitle.featuredArtist
    } yield aList.flatMap(a => getAllNames(a))

    val artistNames =
      (artistNamesFromArtistFiled ++ featuredArtistNamesO.orElse(Some(List[String]())).get)
        .distinct
        .sorted

    s"${artistNames.mkString(", ")} - ${parsedTitle.actualTitle}${parsedTitle.mix.map(" " + _).getOrElse("")}"
  }

  /*
    Рекурсивный парсер дает на выходе right-associative структуру данных, типа
      A & B & C -> Coop(A, Coop(B, C))
    В реальной жизни некоторые из таких комбинаций следует читать по-другому, например
      A & B feat C должно быть Feat(Coop(A, B), C)),
    потому что тут имееется ввиду, что пара A & B используют материал исполнителя C.
   */
  private def fixPriorities(artist : Artist) : Artist = artist match {
    case Coop(a0, Feat(a1, a2)) => Feat(Coop(a0, a1), fixPriorities(a2)) // (A & B) feat C
    case Coop(a0, Pres(a1, a2)) => Pres(Coop(a0, a1), fixPriorities(a2)) // (A & B) pres C
    case Coop(a0, Coop(a1, a2)) => Coop(Coop(a0, a1), fixPriorities(a2))
    case Coop(a0, that) => Coop(a0, fixPriorities(that))

    case Feat(a0, Feat(a1, a2)) => Feat(Feat(a0, a1), fixPriorities(a2))
    case Feat(a0, that) => Feat(a0, fixPriorities(that))

    case Pres(a0, Pres(a1, a2)) => Pres(Pres(a0, a1), fixPriorities(a2))
    case Pres(a0, that) => Pres(a0, fixPriorities(that))

    case _ => artist
  }

  type ActualTitle = String
  type FeaturedArtist = Artist
  /*
    Парсер списка исполнителей подходит и для названия трека с featured исполнителем.
    Например для
      All I Need feat. Nathalie Miranda
    на выходе будет
      Feat(Single(All I Need), Single(Nathalie Miranda))
    где первый Single() - это настоящее название, а второй - имя featured исполнителя.
   */
  private def parseTitlePartUsingArtistsP(s : String) : Option[(ActualTitle, Option[List[FeaturedArtist]])] = {
    artistsP.parse(s) match {
      case Right((_, result)) => result.toList match {
        case Feat(Single(actualTitle), artist) :: tail => Some((actualTitle, Some(artist :: tail)))
        case _ => Some((s, None))
      }
      case _ => None
    }
  }

  object Parser {

    private val mixTokens = List("mix", "remix", "dub", "rub", "instrumental", "original", "extended", "edit")

    private val featTokens = List("featuring", "feat.", "feat", "ft.", "ft")
    private val presTokens = List("presents", "pres.", "pres")

    // <editor-fold desc="Artist parser">

    val pairSep = sp.rep.?.soft ~ P.char(',') ~ sp.rep.?
    val coopSep = sp.rep.?.soft ~ P.char('&') ~ sp.rep.?

    val pres = P.oneOf(presTokens.map(P.ignoreCase))
    val presSep = sp.rep.soft ~ pres ~ sp.rep

    val feat = P.oneOf(featTokens.map(P.ignoreCase))
    val featSep = sp.rep.soft ~ feat ~ sp.rep

    val anySep = presSep.backtrack | featSep.backtrack | pairSep.backtrack | coopSep

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

    /*
  All My Life (feat. Andrea Martin, Sean Declase) ->
    All My Life feat. Andrea Martin, Sean Declase
*/
    def removeParenthesesAroundFeaturedBlock(s : String) : String = {
      val featBlockStart = for {
        start <- findLastParenthesesGroupStart(s)
        featCandidate = s.substring(start+1, s.length-1).toLowerCase
        _ <- featTokens.find(l => featCandidate.startsWith(l))
      } yield start

      featBlockStart.fold(s) { start =>
        s.substring(0, start) + s.substring(start+1, s.length-1)
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
    def extractTitleAndMixName(s : String) : (String, Option[String]) = {
      val partsOption = for {
        start <- findLastParenthesesGroupStart(s)
        mixCandidate = s.substring(start+1, s.length-1).toLowerCase
        _ <- mixTokens.find(l => mixCandidate.endsWith(l)) // (... ... Remix) или (Dub)
        (title, mix) = s.splitAt(start)
      } yield (title.stripTrailing(), mix)

      partsOption match {
        case Some((title, mix)) => (title, Some(mix))
        case None => (s, None)
      }
    }

    /*
Пытается найти индекс символа, с которого начинается последняя группа скобок.
Music Should Not Stop (Jonas Rathsman (PL) Remix)
                      ^
*/
    def findLastParenthesesGroupStart(s : String) : Option[Int] = {
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
  }
}
