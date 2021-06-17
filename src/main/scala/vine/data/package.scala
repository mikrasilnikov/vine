package vine

import zio.Has

package object data {
  type VineDatabase = Has[DatabaseService]
}
