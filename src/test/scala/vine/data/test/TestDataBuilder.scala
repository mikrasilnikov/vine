package vine.data.test

import vine.data.VineDatabaseImpl
import zio.{Has, ZIO}

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

trait TestDataBuilder[Entity] {
  def build : ZIO[Has[VineDatabaseImpl], Throwable, Entity]
}

object TestDataBuilder {
  private val typeCounters = mutable.HashMap[String, Int]()

  def genString[T : ClassTag](prefix : String) : String = {
    this.synchronized {
      val key = s"${classTag[T].toString()}-$prefix"
      val num = typeCounters.getOrElseUpdate(key, 1)
      typeCounters.update(key, num + 1)
      s"${prefix}_$num"
    }
  }
}
