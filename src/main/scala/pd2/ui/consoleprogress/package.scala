package pd2.ui

import pd2.ui.ProgressBar.{Completed, Failed, ItemState}
import zio.{Has, ZIO}
import zio.console.Console
import zio.macros.accessible

package object consoleprogress {

  type ConsoleProgress = Has[ConsoleProgress.Service]

  @accessible
  object ConsoleProgress {

    case class ProgressItem(barLabel: String, index: Int)

    trait Service
    {
      def drawProgress : ZIO[Any, Nothing, Unit]
      def acquireProgressItem(barLabel: String) : ZIO[Any, Nothing, ProgressItem]
      def acquireProgressItems(barLabel : String, amount : Int) : ZIO[Any, Nothing, List[ProgressItem]]
      def updateProgressItem(item: ProgressItem, state: ItemState) : ZIO[Any, Nothing, Unit]

      def completeProgressItem(item : ProgressItem): ZIO[Any, Nothing, Unit] = updateProgressItem(item, Completed)
      def failProgressItem(item : ProgressItem): ZIO[Any, Nothing, Unit] = updateProgressItem(item, Failed)
    }
  }

}