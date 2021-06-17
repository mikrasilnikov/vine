package vine

import zio._
import zio.macros.accessible

package object counters {
  type Counters = Has[Counters.Service]

  @accessible
  object Counters {

    trait Service {
      def modify(name : String, amount : Int) : ZIO[Any, Nothing, Unit]
      def ensureAllZero : Task[Unit]
    }
  }
}
