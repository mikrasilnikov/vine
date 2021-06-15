package pd2.ui

import zio.{Has, ZIO}
import zio.console.Console
import zio.macros.accessible

import java.io.IOException

package object consoleprogress {

  type ConsoleProgress = Has[ConsoleProgress.Service]

  @accessible
  object ConsoleProgress {

    final case class BucketRef(barLabel: String, bucketIndex: Int)

    trait Service
    {
      def initializeBar(label : String, bucketSizes : Seq[Int]) : ZIO[Any, Nothing, Seq[BucketRef]]
      def completeBar(label : String) : ZIO[Any, Nothing, Unit]

      def completeMany(bucketRef : BucketRef, amount : Int) : ZIO[Any, Nothing, Unit]
      def failMany(bucketRef : BucketRef, amount : Int) : ZIO[Any, Nothing, Unit]

      def failAll(bucketRef : BucketRef) : ZIO[Any, Nothing, Unit]

      def completeOne(bucketRef: BucketRef) : ZIO[Any, Nothing, Unit]
      def failOne(bucketRef : BucketRef) : ZIO[Any, Nothing, Unit]

      def drawProgress : ZIO[Any, IOException, Unit]
      def drawProgress(headers : List[String]) : ZIO[Any, IOException, Unit]
      
    }
  }

}
