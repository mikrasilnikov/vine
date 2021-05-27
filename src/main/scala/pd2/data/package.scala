package pd2

import zio.Has

package object data {
  type Pd2Database = Has[DatabaseService]
}
