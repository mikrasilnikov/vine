package pd2.web.test

import pd2.web.TraxsourceDataProvider
import pd2.web.TraxsourceDataProvider.{Present, TraxsourcePage}
import pd2.web.test.TraxsourceDataProviderSuite.{ suite, testM}
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assert}

object TraxsourceServiceDataSuite extends DefaultRunnableSpec with ManagedTestResources {
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
        }
      )
    )
}
