package pd2.ui.consoleprogress

import com.sun.jna.platform.win32.{Kernel32, WinBase, Wincon}
import com.sun.jna.ptr.{IntByReference, PointerByReference}
import pd2.ui.ProgressBar.{ItemState, Pending, ProgressBarDimensions, ProgressBarLayout}
import pd2.ui.consoleprogress.ConsoleProgress.ProgressItem
import pd2.ui.consoleprogress.ConsoleProgressLive.DrawState
import pd2.ui.{ConsoleASCII, ProgressBar}
import zio.system.System
import zio.console.Console
import zio.{Has, Ref, RefM, Task, ZIO, ZLayer}

import java.time.LocalTime
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


  def acquireProgressItemsAfter(barLabel: String, amount : Int, existing : ProgressItem)
    : ZIO[Any, Nothing, List[ProgressItem]] =
  {
    ???
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
  }

  def acquireProgressItem(batchName: String)
    : ZIO[Any, Nothing, ProgressItem] =
    acquireProgressItems(batchName, 1).map(_.head)

  def drawProgress: ZIO[Any, Nothing, Unit] = {
    import java.time.temporal.ChronoUnit
    for {
      state <- drawState.get
      now   <- ZIO.effectTotal(LocalTime.now())
      tick  =  state.startTime.until(now, ChronoUnit.MILLIS) / 333
      _     <- console.putStr(ConsoleASCII.Positioning.up(state.lastNumBarsDrawn))
      _     <- progressBarsRef.modify { bars =>
                for {
                  _ <- ZIO.foreach_(bars)(bar => drawProgressBar(bar, tick))
                  _ <- drawState.modify(s => ((), s.copy(lastNumBarsDrawn = bars.length)))
                } yield ((), bars)
              }
    } yield ()
  }

  private def drawProgressBar(bar: ProgressBar, tick : Long): ZIO[Any, Nothing, Unit] = {
    for {
      _ <- ZIO.succeed()
      render = ProgressBar.render(bar, tick)
      _ <-  if (!runningInsideIntellij)
        console.putStr(ConsoleASCII.Positioning.left(1000)) *> console.putStrLn(render)
      else
        console.putStr("\b" * 100) *> console.putStr(render)
    } yield ()
  }
}

object ConsoleProgressLive {

  case class DrawState(startTime : LocalTime, lastNumBarsDrawn : Int)

  def makeLayer(progressBarDimensions: ProgressBarDimensions)
  : ZLayer[Has[System.Service] with Has[Console.Service], Throwable, ConsoleProgress] =
    ZLayer.fromServicesM[System.Service, Console.Service, Any, Throwable, ConsoleProgress.Service] {
      (system, console) => makeCore(system, console, progressBarDimensions)
    }

  def makeCore(
    system    : System.Service,
    console   : Console.Service,
    dimensions: ProgressBarDimensions)
  : ZIO[Any, Throwable, ConsoleProgressLive] =
   {
      for {
        osOption        <- system.property("os.name")
        win             =  osOption.map(_.toLowerCase.contains("windows")).fold(false)(_ => true)
        insideIntellij  <- runningInsideIntellij(system)
        _               <- enableWindowsTerminalProcessing.when(win & !insideIntellij)
        bars            <- RefM.make(ArrayBuffer.empty[ProgressBar])
        now             <- ZIO.effectTotal(LocalTime.now())
        drawState       <- Ref.make(DrawState(now, lastNumBarsDrawn = 0))
      } yield ConsoleProgressLive(console, bars, drawState, dimensions, insideIntellij)
  }

  private def enableWindowsTerminalProcessing : Task[Unit] =
    ZIO.effect {
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

  private def runningInsideIntellij(systemService : System.Service)
  : ZIO[Any, SecurityException, Boolean] = {
    for {
      propOption <- systemService.env("intellij-terminal")
      ijMode = propOption.fold(false)(_ => true)
    } yield ijMode
  }

}
