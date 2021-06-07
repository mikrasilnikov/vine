package pd2.ui.consoleprogress

import org.fusesource.jansi.Ansi.ansi
import pd2.ui.ProgressBar.{ItemState, Pending, ProgressBarDimensions, ProgressBarLayout}
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import pd2.ui.consoleprogress.ConsoleProgressLive.DrawState
import pd2.ui.ProgressBar
import zio.system.System
import zio.console.{Console, putStrLn}
import zio.{Has, Ref, RefM, Task, ZIO, ZLayer}

import java.io.IOException
import java.time.LocalDateTime
import scala.collection.mutable.ArrayBuffer

final case class ConsoleProgressLive(
  console               : Console.Service,
  progressBarsRef       : RefM[ArrayBuffer[ProgressBar]],
  drawState             : Ref[DrawState],
  defaultDimensions     : ProgressBarDimensions,
  runningInsideIntellij : Boolean)
  extends ConsoleProgress.Service
{
  def updateProgressItem(item : ProgressItem, state: ItemState): ZIO[Any, Nothing, Unit] =
    progressBarsRef.modify { bars =>
      for {
        _         <- ZIO.succeed()
        barIndex  =  bars.indexWhere(bar => bar.layout.label == item.barLabel)
        _         =  bars(barIndex).workItems(item.index) = state
      } yield ((), bars)
    }

  def acquireProgressItems(barLabel: String, amount : Int): ZIO[Any, Nothing, List[ProgressItem]] =
    progressBarsRef.modify { bars =>
      for {
        _ <- ZIO.succeed()
        barIndex = {
          val i = bars.indexWhere(bar => bar.layout.label == barLabel)
          if (i == -1) {
            bars.append(ProgressBar(ArrayBuffer.empty[ItemState], ProgressBarLayout(barLabel, defaultDimensions)))
            bars.length - 1
          } else i
        }
        bar = bars(barIndex)
        wiCount = bar.workItems.length
        range = wiCount until wiCount + amount
        _ = bar.workItems.addAll(range.map(_ => Pending))
        result = range.map(i => ProgressItem(barLabel, i)).toList
      } yield (result, bars)
    }

  def acquireProgressItem(batchName: String)
    : ZIO[Any, Nothing, ProgressItem] =
    acquireProgressItems(batchName, 1).map(_.head)

  def drawProgress: ZIO[Any, IOException, Unit] =
  {
    import java.time.temporal.ChronoUnit
    for {
      state <- drawState.get
      now   <- ZIO.effectTotal(LocalDateTime.now())
      tick  =  state.startTime.until(now, ChronoUnit.MILLIS) / 500

      // Hide cursor
      _ <- console.putStr("\u001b[?25l")

      // Save current cursor position before drawing first frame.
      // Restore initial position before drawing subsequent frames.
      op    <- drawState.modify { state =>
                  if (state.firstFrame) (console.putStr(ansi().saveCursorPosition().toString), state.copy(firstFrame = false))
                  else (console.putStr(ansi().restoreCursorPosition().toString), state)
               }
      _     <- op

      _     <- progressBarsRef.modify { bars =>
                for {
                  _ <- ZIO.foreach_(bars)(bar => drawProgressBar(bar, tick))
                } yield ((), bars)
              }
      // Show cursor
      _ <- console.putStr("\u001b[?25h")
    } yield ()
  }

  private def drawProgressBar(bar: ProgressBar, tick : Long): ZIO[Any, IOException, Unit] =
  {
    for {
      render  <- ZIO.succeed(ProgressBar.render(bar, tick))
      _       <-  if (!runningInsideIntellij)
                  console.putStr(render) *>
                  console.putStr(ansi().cursorDown(1).toString) *>
                  console.putStr(ansi().cursorToColumn(1).toString)
      else
        console.putStr("\b" * 100) *>
        console.putStr(render)
    } yield ()
  }
}

object ConsoleProgressLive {

  case class DrawState(startTime : LocalDateTime, firstFrame : Boolean)

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
        osOption        <- system.property("os.name")
        win             =  osOption.map(_.toLowerCase.contains("windows")).fold(false)(identity)
        insideIntellij  <- runningInsideIntellij(system)
        bars            <- RefM.make(ArrayBuffer.empty[ProgressBar])
        now             <- ZIO.effectTotal(LocalDateTime.now())
        drawState       <- Ref.make(DrawState(startTime = now, firstFrame = true))
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
