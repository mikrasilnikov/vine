import cats.data.NonEmptyList
import cats.parse.Parser.{not, peek}
import cats.parse.{Numbers, Parser0, Parser => P}
import cats.parse.Rfc5234._

import pd2.data.Track._


val mixP = single

/*
  Пытается найти индекс символа, с которого предположительно начинается название микса.
  Music Should Not Stop (Jonas Rathsman (PL) Remix)
                        ^
  Название микса включает в себя внешние скобки.
*/

def findLastParenthesisGroup(s : String) : Option[Int] =
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

val mixLabels = List("mix", "remix", "dub", "rub", "instrumental", "original", "extended")
val mixLabelsSpacePrefixed = mixLabels.map(' ' + _)

def removeMixNameIfPresent(s : String) : String = {
  val mixNameStartPos = for {
    start <- findLastParenthesisGroup(s)
    candidate = s.substring(start + 1, s.length - 1)
    _ <- mixLabelsSpacePrefixed.find(l => candidate.toLowerCase.endsWith(l))
  } yield start

  mixNameStartPos.fold(s)(start => s.substring(0, start).stripTrailing())
}

val withoutMixName = removeMixNameIfPresent(
  "Music Should Not Stop feat. Omar Daini & Some Artist (US) (Jonas Rathsman (PL) Remix)")

artistsP.parse(withoutMixName) match {
  case Right(value) => println(value)
  case _ => ()
}
