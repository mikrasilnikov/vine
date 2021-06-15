package pd2

import pd2.config.ConfigModel.FeedTag
import pd2.providers.beatport.Beatport
import pd2.providers.traxsource.Traxsource
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
