package vine.providers.test

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import vine.providers.test.TraxsourcePageSuite.getClass
import zio.{ZIO, ZManaged}

import java.io.BufferedInputStream
import java.util.zip.{ZipFile, ZipInputStream}
import scala.io.{Codec, Source}

trait ManagedTestResources {

  protected def loadTextFileManaged(name : String) : ZManaged[Any, Throwable, String] =
    ZManaged.make(
      ZIO.effect {
        val zipStream = new ZipInputStream(getClass.getResourceAsStream(name))
        val entry = zipStream.getNextEntry
        val buf = new Array[Byte](entry.getSize.toInt)

        var off = 0
        while (off < buf.length - 1) {
          val bytesRead = zipStream.read(buf, off, buf.size - off)
          off += bytesRead
        }

        Source.fromBytes(buf)(Codec.UTF8)
      })(
      source => ZIO.effectTotal(source.close()))
        .map { s =>s.getLines().mkString("\n") }
}
