package pd2

import pd2.data.test.TrackSuite
import pd2.ui.test.ProgressBarSuite
import pd2.web.test.TraxsourceDataProviderSuite
import zio.test.DefaultRunnableSpec

object AllTests extends DefaultRunnableSpec  {
  override def spec = suite("All tests") (
    TrackSuite.spec,
    ProgressBarSuite.spec,
    TraxsourceDataProviderSuite.spec
  )
}
