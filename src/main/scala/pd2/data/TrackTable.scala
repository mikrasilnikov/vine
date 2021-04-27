package pd2.data

import java.time.LocalDate
import slick.jdbc.SQLiteProfile.api._

//noinspection MutatorLikeMethodIsParameterless
class TrackTable(tag: Tag) extends Table[Track](tag, "tracks") {
  def id          = column[Int]("Id", O.PrimaryKey, O.AutoInc)
  def artist      = column[String]("Artist")
  def title       = column[String]("Title")
  def label       = column[String]("Label")
  def releaseDate = column[LocalDate]("ReleaseDate")
  def feed        = column[String]("Feed")

  def * = (artist, title, label, releaseDate, feed, id).mapTo[Track]
}