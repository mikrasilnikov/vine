import zio.console.{getStrLn, putStr}
import zio.duration.durationInt
import zio.{ExitCode, Ref, Schedule, URIO, ZIO, system}
import pd2.ui._
import com.sun.jna.platform.win32.Kernel32

object TerminalTest extends zio.App {

  object Console {

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

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val setConsoleMode = ZIO.effectTotal(
      Kernel32.INSTANCE.SetConsoleMode(Kernel32.INSTANCE.GetStdHandle(-11), 7))

    //val bar = PercentageBar(33, ProgressBarLayout("test123", 20, 50))

    def drawBar(barRef : Ref[PercentageBar]) = for {
      ijProp <- system.env("intellij-terminal")
      bar <- barRef.get
      render = ProgressBar.render(bar, 0)
      _ <- if (ijProp.isEmpty)
              putStr(Console.Positioning.left(1000)) *> putStr(render)
      else
              putStr("\b").repeatN(render.length) *> putStr(render)
    } yield ()

    val app = for {
      _ <- setConsoleMode
      ref <- Ref.make(PercentageBar(0, 100, ProgressBarLayout("test123", 20, 50)))
      drawingFiber <- drawBar(ref).repeat(Schedule.duration(100.millis)).forever.fork
      updatingFiber <- ref
        .modify(bar => ((), bar.copy(current = bar.current + 1)))
        .repeat(Schedule.duration(200.millis))
        .forever.fork
      _ <- getStrLn
      _ <- drawingFiber.interrupt *> updatingFiber.interrupt
    } yield ()

    app.exitCode
  }
}
