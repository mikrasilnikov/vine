package pd2

import pd2.data.test.TrackSuite
import pd2.providers.filters.test.OnlyNewFilterSuite
import pd2.ui.test.ProgressBarSuite
import pd2.providers.test.{TraxsourceServiceTrackSuite, TraxsourcePageSuite}
import zio.test.DefaultRunnableSpec

object AllTests extends DefaultRunnableSpec  {
  override def spec = suite("All tests") (
    TrackSuite.spec,
    ProgressBarSuite.spec,
    TraxsourcePageSuite.spec,
    TraxsourceServiceTrackSuite.spec,
    OnlyNewFilterSuite.spec
  )
}
