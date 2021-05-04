package pd2.ui

import com.sun.jna.platform.win32.{Kernel32, Wincon}
import com.sun.jna.platform.win32.Kernel32.INSTANCE
import com.sun.jna.ptr.IntByReference
import zio.console.Console
import zio.macros.accessible
import zio.system.System
import zio.{Has, Ref, Task, UIO, ZIO, ZLayer, system}

@accessible
object ConsoleUILayer {
  type ConsoleUI = Has[ConsoleUILayer.Service]

  trait Service {
    def drawProgressBar(barRef : Ref[ProgressBar]) : ZIO[Console, Nothing, Unit]
  }

  val live: ZLayer[System, Throwable, Has[ConsoleUILayer.Service]] = {
    for {
      osOption <- system.property("os.name")
      win = osOption.map(_.toLowerCase.contains("windows")).fold(false)(w => w)
      insideIntellij <- runningInsideIntellij
      _ <- setConsoleMode.when(win & !insideIntellij)
    } yield new ConsoleUILive(insideIntellij)
  }.toLayer

  def setConsoleMode: Task[Unit] = ZIO.effect {
    import Kernel32.{INSTANCE => k32}
    val currentModeRef = new IntByReference()
    val hConsole = k32.GetStdHandle(Wincon.STD_OUTPUT_HANDLE)
    if (k32.GetConsoleMode(hConsole, currentModeRef)) {
      k32.SetConsoleMode(hConsole, currentModeRef.getValue | Wincon.ENABLE_VIRTUAL_TERMINAL_PROCESSING)
      ()
    } else {
      throw new Exception(
        s"Could not set ENABLE_VIRTUAL_TERMINAL_PROCESSING. Last error is ${k32.GetLastError()}")
    }
  }

  def runningInsideIntellij : ZIO[System, SecurityException, Boolean] = {
    for {
      propOption <- system.env("intellij-terminal")
      ijMode = propOption.fold(false)(_ => true)
    } yield ijMode
  }
}
