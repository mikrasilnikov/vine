package pd2.providers

import zio._
import zio.macros.accessible

package object traxsource {

  type Traxsource = Has[Traxsource.Service]

  @accessible
  object Traxsource
  {
    trait Service extends MusicStoreDataProvider
  }
}
