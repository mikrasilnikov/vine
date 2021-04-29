package pd2.data

import cats.data.NonEmptyList
import cats.parse.Parser.not
import cats.parse.Rfc5234.{char, sp}
import cats.parse.{Parser => P}

import java.time.LocalDate

case class Track(artist: String, title: String, label: String, releaseDate: LocalDate, feed: String, id: Int = 0)




object Track {
  def tupled = (Track.apply _).tupled




}

