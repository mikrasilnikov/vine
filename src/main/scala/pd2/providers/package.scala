package pd2

package object providers {
  case class Pager(current: Int, last: Int) {
    val remainingPages: List[Int] = (current + 1 to last).toList
  }
}
