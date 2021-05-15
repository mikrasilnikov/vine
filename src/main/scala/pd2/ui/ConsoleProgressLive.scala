package pd2.ui
import com.sun.jna.platform.win32.{Kernel32, WinBase, Wincon}
import com.sun.jna.ptr.{IntByReference, PointerByReference}
import pd2.ui.ConsoleProgressLive.DrawState
import pd2.ui.ConsoleProgressService.{ConsoleProgress, ProgressItem, Service}
import pd2.ui.ProgressBar.{ItemState, Pending, ProgressBarDimensions, ProgressBarLayout}
import zio.console._
import zio.system.System
import zio.{Ref, RefM, Task, ZIO, ZLayer, system}

import scala.collection.mutable.ArrayBuffer

final case class ConsoleProgressLive(
  progressBarsRef       : RefM[ArrayBuffer[ProgressBar]],
  drawState             : Ref[DrawState],
  defaultDimensions     : ProgressBarDimensions,
  runningInsideIntellij : Boolean)
  extends ConsoleProgressService.Service {

  def updateProgressItem(item : ProgressItem, state: ItemState): ZIO[Any, Nothing, Unit] =
    progressBarsRef.modify { bars =>
      for {
        _         <- ZIO.succeed()
        barIndex  =  bars.indexWhere(bar => bar.layout.label == item.barLabel)
        _         =  bars(barIndex).workItems(item.index) = state
      } yield ((), bars)
    }

  def acquireProgressItems(barLabel: String, amount : Int)
    : ZIO[Any, Nothing, List[ProgressItem]] =
  progressBarsRef.modify
  {
    bars =>  for {
      _         <- ZIO.succeed()
      barIndex  = {
        val i = bars.indexWhere(bar => bar.layout.label == barLabel)
        if (i == -1) {
          bars.append(ProgressBar(ArrayBuffer.empty[ItemState], ProgressBarLayout(barLabel, defaultDimensions)))
          bars.length - 1
        } else i
      }
      bar     = bars(barIndex)
      wiCount = bar.workItems.length
      range   = wiCount until wiCount + amount
      _       = bar.workItems.addAll(range.map(_ => Pending))
      result  = range.map(i => new ProgressItem(barLabel, i)).toList

    } yield (result, bars)
  }

  def acquireProgressItem(batchName: String)
    : ZIO[Any, Nothing, ProgressItem] =
    acquireProgressItems(batchName, 1).map(_.head)

  def drawProgress: ZIO[Console, Nothing, Unit] = {
    for {
      state <- drawState.get
      _     <- putStr(ConsoleASCII.Positioning.up(state.lastNumBarsDrawn))
      _     <- progressBarsRef.modify { bars =>
                for {
                  _ <- ZIO.foreach_(bars)(bar => drawProgressBar(bar))
                  _ <- drawState.modify(_ => ((), DrawState(bars.length)))
                } yield ((), bars)
              }
    } yield ()
  }

  private def drawProgressBar(bar: ProgressBar): ZIO[Console, Nothing, Unit] = {
    for {
      _ <- ZIO.succeed()
      render = ProgressBar.render(bar)
      _ <-  if (!runningInsideIntellij)
        putStr(ConsoleASCII.Positioning.left(1000)) *> putStr(render)
      else
        putStr("\b" * 100) *> putStr(render)
    } yield ()
  }
}

object ConsoleProgressLive {

  case class DrawState(lastNumBarsDrawn : Int)

  def make(progressBarDimensions : ProgressBarDimensions): ZLayer[System, Throwable, ConsoleProgress] =
    {
      for {
        osOption        <- system.property("os.name")
        win             =  osOption.map(_.toLowerCase.contains("windows")).fold(false)(_ => true)
        insideIntellij  <- runningInsideIntellij
        _               <- enableWindowsTerminalProcessing.when(win & !insideIntellij)
        bars            <- RefM.make(ArrayBuffer.empty[ProgressBar])
        drawState       <- Ref.make(DrawState(0))
      } yield ConsoleProgressLive(bars, drawState, progressBarDimensions, insideIntellij)
  }.toLayer

  private def enableWindowsTerminalProcessing: Task[Unit] = ZIO.effect {
    import Kernel32.{INSTANCE => k32}
    val currentModeRef = new IntByReference()
    val hConsole = k32.GetStdHandle(Wincon.STD_OUTPUT_HANDLE)
    if (k32.GetConsoleMode(hConsole, currentModeRef)) {
      k32.SetConsoleMode(hConsole, currentModeRef.getValue | Wincon.ENABLE_VIRTUAL_TERMINAL_PROCESSING)
      ()
    } else {
      val buffer = new PointerByReference()

      val errorCode = k32.GetLastError()
      val msgSize = k32.FormatMessage(
        WinBase.FORMAT_MESSAGE_FROM_SYSTEM |
          WinBase.FORMAT_MESSAGE_ALLOCATE_BUFFER,
        null,
        errorCode,
        0,
        buffer,
        0,
        null)

      val message = String.valueOf(
        buffer.getPointer.getPointer(0).getCharArray(0, 24))

      throw new Exception(message)
    }
  }

  private def runningInsideIntellij : ZIO[System, SecurityException, Boolean] = {
    for {
      propOption <- system.env("intellij-terminal")
      ijMode = propOption.fold(false)(_ => true)
    } yield ijMode
  }

}
