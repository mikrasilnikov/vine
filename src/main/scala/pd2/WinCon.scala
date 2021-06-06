package pd2

import com.sun.jna._
import com.sun.jna.platform.win32._
import com.sun.jna.ptr._

object WinCon {

  val kernel32 : Kernel32 = Kernel32.INSTANCE

  def main(args: Array[String]): Unit = {

    kernel32.SetLastError(123)
    getLastError.fold(println("success"))(println)

    println("kernel32.GetCurrentThread()")
    kernel32.GetCurrentThread()
    getLastError.fold(println("success"))(println)

    println("kernel32.CloseHandle(WinBase.INVALID_HANDLE_VALUE)")
    kernel32.CloseHandle(WinBase.INVALID_HANDLE_VALUE)
    getLastError.fold(println("success"))(println)

    println("kernel32.GetStdHandle(Wincon.STD_OUTPUT_HANDLE)")
    val hStdout = kernel32.GetStdHandle(Wincon.STD_OUTPUT_HANDLE)
    getLastError.fold(println("success"))(println)

    println("kernel32.GetConsoleScreenBufferInfo(hStdout, info)")
    val info = new Wincon.CONSOLE_SCREEN_BUFFER_INFO
    kernel32.GetConsoleScreenBufferInfo(hStdout, info)
    getLastError.fold(println("success"))(println)

    println(info)

  }

  def getLastError : Option[String] = {
    Native.getLastError match {
      case 0 => None
      case code =>
        val buffer = new PointerByReference
        kernel32.FormatMessage(
          WinBase.FORMAT_MESSAGE_FROM_SYSTEM | WinBase.FORMAT_MESSAGE_ALLOCATE_BUFFER,
          null, code, 0, buffer, 0, null)
        val s = buffer.getValue.getWideString(0)
        Kernel32.INSTANCE.LocalFree(buffer.getValue)
        Some(s"$code: ${s.trim}")
    }
  }
}
