package vine

import vine.config.ConfigModel.FeedTag
import vine.providers.beatport.Beatport
import vine.providers.traxsource.Traxsource
import zio.{URIO, ZIO}

package object providers {

  def getProviderByFeedTag(tag : FeedTag): URIO[Beatport with Traxsource, MusicStoreDataProvider] =
    tag match {
      case FeedTag.BeatportFeed => ZIO.service[Beatport.Service]
      case FeedTag.TraxsourceFeed => ZIO.service[Traxsource.Service]
    }

  case class Pager(current: Int, last: Int) {
    val remainingPages: List[Int] = (current + 1 to last).toList
  }
}
