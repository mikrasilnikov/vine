package pd2.providers.test

import pd2.providers.traxsource.TraxsourcePage.{Absent, Present}
import pd2.providers.traxsource.TraxsourcePage
import zio.ZIO
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, _}

object TraxsourcePageSuite extends DefaultRunnableSpec with ManagedTestResources {

  override def spec =
    suite("TraxsourceDataProviderSuite")(

      suite("Parsing")(

        testM("Traxsource_JustAdded_FirstPage.html") {
          val expected = TraxsourcePage(
            Present(1,314),
            List(8803989, 8803991, 8803992, 8803994, 8803995, 8804004, 8804012, 8804015, 8746381, 8746382))

          loadTextFileManaged("/Traxsource_JustAdded_FirstPage.html.zip")
            .use { doc => ZIO.fromEither(TraxsourcePage.parse(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

        testM("Traxsource_JustAdded_MidPage.html") {
          val expected = TraxsourcePage(
            Present(4,314),
            List(8746403, 8746404, 8746405, 8746406, 8746407, 8746408, 8746409, 8746410, 8781189, 8781190))

          loadTextFileManaged("/Traxsource_JustAdded_MidPage.html.zip")
            .use { doc => ZIO.fromEither(TraxsourcePage.parse(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

        testM("Traxsource_JustAdded_LastPage.html") {
          val expected = TraxsourcePage(
            Present(2123, 2123),
            List(8766092, 8766093, 8766094))

          loadTextFileManaged("/Traxsource_JustAdded_LastPage.html.zip")
            .use { doc => ZIO.fromEither(TraxsourcePage.parse(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

        testM("Traxsource_Top100.html") {
          val expected = TraxsourcePage(
            Absent,
            List(8761057, 8723962, 8766074, 8646678, 8559344, 8751000, 8719077, 8680815, 8702697, 8432668))

          loadTextFileManaged("/Traxsource_Top100.html.zip")
            .use { doc => ZIO.fromEither(TraxsourcePage.parse(doc)) }
            .map(res => assert(res)(equalTo(expected)))
        },

      )
    )
}
