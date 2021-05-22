package pd2.providers

import pd2.data.TrackParsing
import pd2.data.TrackParsing.rewriteTrackName

case class TrackDto(
  artist : String,
  title : String,
  label : String,
  internalId : Int)
{
  val uniqueName: Option[String] = for {
    artists <- TrackParsing.parseArtists(artist)
    title <- TrackParsing.parseTitle(title)
  } yield rewriteTrackName(artists, title)
}
