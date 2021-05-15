package pd2.ui

import pd2.ui.ProgressBar.{Completed, Failed, ItemState}
import zio.console.Console
import zio.macros.accessible
import zio.{Has, ZIO}

@accessible
object ConsoleProgressService {
  type ConsoleProgress = Has[ConsoleProgressService.Service]

  case class ProgressItem(barLabel: String, index: Int)

  trait Service
  {
    def drawProgress : ZIO[Console, Nothing, Unit]
    def acquireProgressItem(barLabel: String) : ZIO[Any, Nothing, ProgressItem]
    def acquireProgressItems(barLabel : String, amount : Int) : ZIO[Any, Nothing, List[ProgressItem]]
    def updateProgressItem(item: ProgressItem, state: ItemState) : ZIO[Any, Nothing, Unit]

    def completeProgressItem(item : ProgressItem): ZIO[Any, Nothing, Unit] = updateProgressItem(item, Completed)
    def failProgressItem(item : ProgressItem): ZIO[Any, Nothing, Unit] = updateProgressItem(item, Failed)

    def withProgressReporting[R, E, A](barLabel : String)(effect : ZIO[R, E, A])
      : ZIO[R, E, A] = {
      for {
        item    <- acquireProgressItem(barLabel)
        _       <- updateProgressItem(item, ProgressBar.InProgress)
        result  <- effect.tapError(_ => failProgressItem(item))
        _       <- completeProgressItem(item)
      } yield result
    }
  }
}
