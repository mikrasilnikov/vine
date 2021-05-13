package pd2.ui
import com.sun.jna.platform.win32.{Kernel32, Wincon}
import com.sun.jna.ptr.IntByReference
import zio.console._
import zio.system._
import zio.{Ref, UIO, ZIO, system}

class ConsoleUILive(runningInsideIntellij : Boolean) extends ConsoleUIService.Service {

  object ConsoleASCII {

    object Color {
      val reset =   "\u001b[0m"
      val black =   "\u001b[30m"
      val white =   "\u001b[37m"
      val brightWhite = "\u001b[37;1m"
      val red =     "\u001b[31m"
      val brightRed = "\u001b[31;1m"
      val green =   "\u001b[32m"
      val brightGreen = "\u001b[32;1m"
      val yellow =  "\u001b[33m"
      val brightYellow = "\u001b[33;1m"
      val blue =    "\u001b[34m"
      val brightBlue = "\u001b[34;1m"
      val magenta = "\u001b[35m"
      val brightMagenta = "\u001b[35;1m"
      val cyan =    "\u001b[36m"
      val brightCyan = "\u001b[36;1m"

      object Background {
        val black = "\u001b[40m"
        val red = "\u001b[41m"
        val green = "\u001b[42m"
        val yellow = "\u001b[43m"
        val blue = "\u001b[44m"
        val magenta = "\u001b[45m"
        val cyan = "\u001b[46m"
        val white = "\u001b[47m"

        val brightBlack = "\u001b[40;1m"
        val brightRed = "\u001b[41;1m"
        val brightGreen = "\u001b[42;1m"
        val brightYellow = "\u001b[43;1m"
        val brightBlue = "\u001b[44;1m"
        val brightMagenta = "\u001b[45;1m"
        val brightCyan = "\u001b[46;1m"
        val brightWhite = "\u001b[47;1m"
      }

    }

    object Decorations {
      val bold = "\u001b[1m"
      val underline = "\u001b[4m"
      val reversed = "\u001b[7m"
    }

    object Positioning {
      def up(n: Int) = s"\u001b[${n}A"
      def down(n: Int) = s"\u001b[${n}B"
      def right(n: Int) = s"\u001b[${n}C"
      def left(n: Int) = s"\u001b[${n}D"
    }
  }

  def drawProgressBar(barRef: Ref[ProgressBar]): ZIO[Console, Nothing, Unit] = {
    for {
      bar <- barRef.get
      render = ProgressBar.render(bar)
      _ <-  if (!runningInsideIntellij)
              putStr(ConsoleASCII.Positioning.left(1000)) *> putStr(render)
            else
              putStr("\b" * 100) *> putStr(render)
    } yield ()
  }

  override def aquireProgressItem(batchName: String): ZIO[Any, Nothing, ConsoleUIService.ProgressItemRef] = ???

  override def updateProgressItem(item: ConsoleUIService.ProgressItemRef, state: ProgressBar.ItemState): ZIO[Any, Nothing, Unit] = ???
}
