package pd2.web.test

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import pd2.ui.test.ProgressBarSuite.test
import pd2.web.TraxsourceDataProvider
import pd2.web.TraxsourceDataProvider.{Absent, Present, TraxsourcePage}
import zio.{ZIO, ZManaged}
import zio.test.DefaultRunnableSpec
import zio.test._
import zio.test.Assertion._
import zio.test.DefaultRunnableSpec

import scala.io.Source

object TraxsourceDataProviderSuite extends DefaultRunnableSpec with ManagedTestResources {

  override def spec =
    suite("TraxsourceDataProviderSuite")(

      suite("Parsing")(

        testM("Traxsource_JustAdded_FirstPage.html") {
          val expected = TraxsourcePage(
            Present(1,314),
            List(8803989, 8803991, 8803992, 8803994, 8803995, 8804004, 8804012, 8804015, 8746381, 8746382))

          loadJsoupManaged("/Traxsource_JustAdded_FirstPage.html")
            .use { doc => ZIO.fromEither(TraxsourceDataProvider.parseTraxsourcePage(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

        testM("Traxsource_JustAdded_MidPage.html") {
          val expected = TraxsourcePage(
            Present(4,314),
            List(8746403, 8746404, 8746405, 8746406, 8746407, 8746408, 8746409, 8746410, 8781189, 8781190))

          loadJsoupManaged("/Traxsource_JustAdded_MidPage.html")
            .use { doc => ZIO.fromEither(TraxsourceDataProvider.parseTraxsourcePage(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

        testM("Traxsource_JustAdded_LastPage.html") {
          val expected = TraxsourcePage(
            Present(2123, 2123),
            List(8766092, 8766093, 8766094))

          loadJsoupManaged("/Traxsource_JustAdded_LastPage.html")
            .use { doc => ZIO.fromEither(TraxsourceDataProvider.parseTraxsourcePage(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

        testM("Traxsource_Top100.html") {
          val expected = TraxsourcePage(
            Absent,
            List(8761057, 8723962, 8766074, 8646678, 8559344, 8751000, 8719077, 8680815, 8702697, 8432668))

          loadJsoupManaged("/Traxsource_Top100.html")
            .use { doc => ZIO.fromEither(TraxsourceDataProvider.parseTraxsourcePage(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

      )
    )
}
