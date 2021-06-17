package vine

import zio._

package object data {
  type VineDatabase = Has[VineDatabaseImpl]
}
