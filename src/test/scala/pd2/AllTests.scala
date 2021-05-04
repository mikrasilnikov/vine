package pd2

import pd2.data.test.TrackSuite
import zio.test._
import zio.test.Assertion._
import pd2.data.test.TrackSuite.suite
import pd2.ui.test.ProgressBarSuite
import zio.test.DefaultRunnableSpec

object AllTests extends DefaultRunnableSpec  {
  override def spec = suite("All tests") (
    TrackSuite.spec,
    ProgressBarSuite.spec
  )
}
