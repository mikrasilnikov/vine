package pd2.providers.test

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.browser.JsoupBrowser.JsoupDocument
import pd2.providers.test.TraxsourceWebPageSuite.getClass
import zio.{ZIO, ZManaged}

import scala.io.Source

trait ManagedTestResources {

  protected def loadTextFileManaged(name : String) : ZManaged[Any, Throwable, String] =
    ZManaged.make(
      ZIO.effect(Source.fromURL(getClass.getResource(name))))(
      source => ZIO.effectTotal(source.close()))
        .map(s => s.getLines().mkString("\n"))

/*  protected def loadJsoupManaged(name : String): ZManaged[Any, Throwable, JsoupDocument] =
    loadTextFileManaged(name)
      .map { s =>
        val browser = new JsoupBrowser()
        browser.parseString(s)
      }*/
}
