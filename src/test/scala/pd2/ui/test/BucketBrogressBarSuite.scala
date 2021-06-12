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

      test("000% Completed") {
        val buckets = Vector[ProgressBucket](ProgressBucket(size = 10, completed = 0, failed = 0))
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [          ]   0%"))
      },

      test("100% Completed") {
        val buckets = Vector[ProgressBucket](ProgressBucket(size = 10, completed = 10, failed = 0))
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [==========] 100%"))
      },

      test("50% Completed, 50% Failed") {
        val buckets = Vector[ProgressBucket](ProgressBucket(size = 10, completed = 5, failed = 5))
        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        val expected = "test  [!!!!!=====] 100%"

        assert(render(bar))(equalTo(expected))
      },

      test("Two buckets, 59% each") {

        val buckets = Vector[ProgressBucket](
          ProgressBucket(size = 100, completed = 59, failed = 0),
          ProgressBucket(size = 100, completed = 59, failed = 0))

        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [==   ==   ]  59%"))
      },

      test("Two buckets, 60% each") {

        val buckets = Vector[ProgressBucket](
          ProgressBucket(size = 100, completed = 60, failed = 0),
          ProgressBucket(size = 100, completed = 60, failed = 0))

        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        assert(render(bar))(equalTo("test  [===  ===  ]  60%"))
      },

      test("Two buckets, 50% each, one has failed item") {
        val buckets = Vector[ProgressBucket](
          ProgressBucket(size = 10, completed = 4, failed = 1),
          ProgressBucket(size = 10, completed = 5, failed = 0))

        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        val expected = "test  [!=   ==   ]  50%"

        assert(render(bar))(equalTo(expected))
      },

      test("Lot of buckets, 50% completed") {
        val buckets1 = (1 to 5000).map(_ => ProgressBucket(size = 10, completed = 10, failed = 0))
        val buckets2 = (1 to 5000).map(_ => ProgressBucket(size = 10, completed = 0, failed = 0))

        val buckets = (buckets1 ++ buckets2).toVector

        val bar = BucketProgressBar(buckets, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))

        val expected = "test  [=====     ]  50%"

        assert(render(bar))(equalTo(expected))
      },

      test("MergeSection - empty") {
        val actual = BucketProgressBar.mergeSections(List[Section]())
        val expected = List[Section]()
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - one") {
        val s = Section(1, 2, value = true)
        val actual = BucketProgressBar.mergeSections(s :: Nil)
        val expected = s :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-true") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(2, 3, value = true)
        val s = Section(1, 3, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: Nil)
        val expected = s :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - false-false") {
        val s1 = Section(1, 2, value = false)
        val s2 = Section(2, 3, value = false)
        val s = Section(1, 3, value = false)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: Nil)
        val expected = s :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-false") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(2, 3, value = false)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: Nil)
        val expected = s1 :: s2 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-_-true") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(3, 4, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: Nil)
        val expected = s1 :: s2 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-false-true") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(2, 3, value = false)
        val s3 = Section(3, 4, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: s3 :: Nil)
        val expected = s1 :: s2 :: s3 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-true-false") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(2, 3, value = true)
        val s3 = Section(3, 4, value = false)

        val s12 = Section(1, 3, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: s3 :: Nil)
        val expected = s12 :: s3 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-false-false") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(2, 3, value = false)
        val s3 = Section(3, 4, value = false)

        val s23 = Section(2, 4, value = false)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: s3 :: Nil)
        val expected = s1 :: s23 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - true-true-true") {
        val s1 = Section(1, 2, value = true)
        val s2 = Section(2, 3, value = true)
        val s3 = Section(3, 4, value = true)

        val s123 = Section(1, 4, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: s3 :: Nil)
        val expected = s123 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - false-true-true-false") {
        val s1 = Section(1, 2, value = false)
        val s2 = Section(2, 3, value = true)
        val s3 = Section(3, 4, value = true)
        val s4 = Section(4, 5, value = false)

        val s23 = Section(2, 4, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: s3 :: s4 :: Nil)
        val expected = s1 :: s23 :: s4 :: Nil
        assert(expected)(equalTo(actual))
      },

      test("MergeSection - false-false-true-true") {
        val s1 = Section(1, 2, value = false)
        val s2 = Section(2, 3, value = false)
        val s3 = Section(3, 4, value = true)
        val s4 = Section(4, 5, value = true)

        val s12 = Section(1, 3, value = false)
        val s34 = Section(3, 5, value = true)

        val actual = BucketProgressBar.mergeSections(s1 :: s2 :: s3 :: s4 :: Nil)
        val expected = s12 :: s34 :: Nil
        assert(expected)(equalTo(actual))
      },

    )
  }
}
