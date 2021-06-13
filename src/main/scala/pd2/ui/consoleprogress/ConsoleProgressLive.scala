package pd2.ui.consoleprogress

import org.fusesource.jansi.Ansi.ansi
import pd2.ui.consoleprogress.ConsoleProgressLive.DrawState
import pd2.ui._
import pd2.ui.consoleprogress.ConsoleProgress.BucketRef
import zio.console.Console
import zio.system.System
import zio._
import java.io.IOException

final case class ConsoleProgressLive(
  console               : Console.Service,
  progressBarsRef       : RefM[Vector[BucketProgressBar]],
  drawState             : RefM[DrawState],
  defaultDimensions     : ProgressBarDimensions,
  runningInsideIntellij : Boolean)
  extends ConsoleProgress.Service
{
  def initializeBar(label: String, bucketSizes: Seq[Int])
  : ZIO[Any, Nothing, Seq[BucketRef]] = {
    progressBarsRef.modify { bars =>
      ZIO.succeed {
        val newBuckets = bucketSizes.map(i => ProgressBucket(i, 0, 0)).toVector
        val refs = bucketSizes.zipWithIndex.map { case (_, index) => BucketRef(label, index) }

        val existingBarIndex = bars.indexWhere(_.layout.label == label)
        existingBarIndex match {
          case -1 =>
            val newBar = BucketProgressBar(newBuckets, ProgressBarLayout(label, defaultDimensions))
            (refs, bars.appended(newBar))
          case i  =>
            val newBar = bars(i).copy(buckets = newBuckets)
            (refs, bars.updated(i, newBar))
        }
      }
    }
  }

  def completeBar(label: String): ZIO[Any, Nothing, Unit] = {
    progressBarsRef.modify { bars =>
      ZIO.succeed {
        val existingBarIndex = bars.indexWhere(_.layout.label == label)
        existingBarIndex match {
          // If there is no existing bar with provided label we append new bar which is completed.
          case -1 =>
            val newBar = BucketProgressBar(
              Vector[ProgressBucket](ProgressBucket(size = 1, completed = 1, failed = 0)),
              ProgressBarLayout(label, defaultDimensions))
            ((), bars.appended(newBar))
          case i =>
            // Preserving failed items count.
            val completedBuckets = bars(i).buckets.map(b => ProgressBucket(b.size, b.size - b.failed, b.failed))
            val newBar = bars(i).copy(buckets = completedBuckets)
            ((), bars.updated(i, newBar))
        }
      }
    }
  }

  def completeOne(bucketRef: BucketRef): ZIO[Any, Nothing, Unit] = modify(bucketRef, true, 1)
  def failOne(bucketRef: BucketRef): ZIO[Any, Nothing, Unit] = modify(bucketRef, false, 1)

  def completeMany(bucketRef: BucketRef, amount : Int): ZIO[Any, Nothing, Unit] = modify(bucketRef, true, amount)
  def failMany(bucketRef: BucketRef, amount : Int): ZIO[Any, Nothing, Unit] = modify(bucketRef, false, amount)

  def failAll(bucketRef: BucketRef) : ZIO[Any, Nothing, Unit] = {
    progressBarsRef.modify { bars =>
      ZIO.succeed {
        val barIndex = bars.indexWhere(_.layout.label == bucketRef.barLabel)
        barIndex match {
          case -1 => // This is a defect
            throw new IllegalStateException(s"Could not find progress bar with label '${bucketRef.barLabel}'")
          case i =>
            val j = bucketRef.bucketIndex
            val oldBucket = bars(i).buckets(j)
            val newBucket = oldBucket.copy(completed = 0, failed = oldBucket.size)
            val updatedBuckets = bars(i).buckets.updated(j, newBucket)
            ((), bars.updated(i, bars(i).copy(buckets = updatedBuckets)))
        }
      }
    }
  }

  private def modify(bucketRef: BucketRef, completed : Boolean, amount : Int): ZIO[Any, Nothing, Unit] =
    progressBarsRef.modify { bars =>
      ZIO.succeed {
        val barIndex = bars.indexWhere(_.layout.label == bucketRef.barLabel)
        barIndex match {
          case -1 => // This is a defect
            throw new IllegalStateException(s"Could not find progress bar with label '${bucketRef.barLabel}'")
          case i =>
            val j = bucketRef.bucketIndex
            val oldBucket = bars(i).buckets(j)
            val newBucket =
              if (completed) oldBucket.copy(completed = oldBucket.completed + amount)
              else oldBucket.copy(failed = oldBucket.failed + amount)
            val updatedBuckets = bars(i).buckets.updated(j, newBucket)
            ((), bars.updated(i, bars(i).copy(buckets = updatedBuckets)))
        }
      }
    }



  def drawProgress: ZIO[Any, IOException, Unit] = {
    for {
      _ <- console.putStr("\u001b[?25l") // Hide cursor
      _ <- drawState.modify { state =>
            if (state.firstFrame)
              console.putStr(ansi().eraseScreen().cursor(0,0).toString)
                .as((), state.copy(firstFrame = false))
            else
              console.putStr(ansi().cursor(0,0).toString)
                .as((), state)
           }
      _ <- progressBarsRef.update { bars =>
              for {
                _ <- ZIO.foreach_(bars)(bar => drawProgressBar(bar))
              } yield bars
            }
      _ <- console.putStr("\u001b[?25h") // Show cursor
    } yield ()
  }

  private def drawProgressBar(bar: BucketProgressBar): ZIO[Any, IOException, Unit] = {
    for {
      //_ <- ZIO.succeed(println(s"\n${bar.buckets.map(b => (b.size, b.completed, b.failed))}"))
      render  <- ZIO.succeed(BucketProgressBar.render(bar))
      _       <-  if (!runningInsideIntellij) {
                    console.putStr(ansi().eraseLine().toString) *>
                    console.putStr(render) *>
                    console.putStr(ansi().cursorDown(1).toString) *>
                    console.putStr(ansi().cursorToColumn(1).toString)
                  } else
                    console.putStr("\b" * 100) *>
                    console.putStr(render)
    } yield ()
  }
}

object ConsoleProgressLive {

  case class DrawState(firstFrame : Boolean)

  def makeLayer(progressBarDimensions: ProgressBarDimensions)
    : ZLayer[System with Console, Throwable, ConsoleProgress] =
  ZLayer.fromServicesM[System.Service, Console.Service, Any, Throwable, ConsoleProgress.Service] {
    (system, console) => makeCore(system, console, progressBarDimensions)
  }

  def makeCore(
    system      : System.Service,
    console     : Console.Service,
    dimensions  : ProgressBarDimensions)
  : ZIO[Any, Throwable, ConsoleProgressLive] =
   {
      for {
        insideIntellij  <- runningInsideIntellij(system)
        bars            <- RefM.make(Vector.empty[BucketProgressBar])
        drawState       <- RefM.make(DrawState(firstFrame = true))
      } yield ConsoleProgressLive(console, bars, drawState, dimensions, insideIntellij)
  }

  private def runningInsideIntellij(systemService : System.Service)
  : ZIO[Any, SecurityException, Boolean] = {
    for {
      propOption <- systemService.env("intellij-terminal")
      ijMode = propOption.fold(false)(_ => true)
    } yield ijMode
  }

}
