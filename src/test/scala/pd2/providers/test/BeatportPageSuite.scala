package pd2.providers.test


import zio.test._
import zio.test.Assertion.anything
import zio.test.DefaultRunnableSpec

object BeatportPageSuite extends DefaultRunnableSpec with ManagedTestResources {

  override def spec =
    suite("TraxsourceDataProviderSuite")(
      test("Traxsource_JustAdded_FirstPage.html") {
        assert(())(anything)
      }
    )
}
