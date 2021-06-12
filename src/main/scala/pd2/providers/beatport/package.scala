package pd2.providers

import zio._
import zio.macros.accessible

import java.time.LocalDate

package object beatport {
  type Beatport = Has[Beatport.Service]

  @accessible
  object Beatport
  {
    trait Service extends MusicStoreDataProvider
  }
}
