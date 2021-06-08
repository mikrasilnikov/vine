package pd2.ui.test

import org.fusesource.jansi.Ansi.ansi
import pd2.ui._
import zio.test._
import zio.test.Assertion._

object BucketProgressBarSuite extends DefaultRunnableSpec {
  import BucketProgressBar._

  def spec = {
    suite("ProgressBar Suite") (

      test("empty bar") {
        val buckets = Vector[ProgressBucket]()
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [          ]   0%"))
      },

      test("0%") {
        val buckets = Vector[ProgressBucket](ProgressBucket(size = 10, completed = 0, failed = 0))
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [          ]   0%"))
      },

      test("100%") {
        val buckets = Vector[ProgressBucket](ProgressBucket(size = 10, completed = 10, failed = 0))
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [==========] 100%"))
      },

      test("50% Completed, 50% Failed") {
        val buckets = Vector[ProgressBucket](ProgressBucket(size = 10, completed = 5, failed = 5))
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        val expected = ansi().a("test  [").bgRed().a("==========").reset().a("] 100%").toString

        assert(render(bar))(equalTo(expected))
      },

      test("Two buckets, 50% each") {

        val buckets = Vector[ProgressBucket](
          ProgressBucket(size = 10, completed = 5, failed = 0),
          ProgressBucket(size = 10, completed = 5, failed = 0))

        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [===  ===  ]  50%"))
      },

      test("Two buckets, 50% each, one has failed item") {
        val buckets = Vector[ProgressBucket](
          ProgressBucket(size = 10, completed = 4, failed = 1),
          ProgressBucket(size = 10, completed = 5, failed = 0))

        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        val expected = ansi().a("test  [").bgRed().a("===  ").reset().a("===  ]  50%").toString

        assert(render(bar))(equalTo(expected))
      }
    )
  }
}
