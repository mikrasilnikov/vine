package pd2.ui.test

import zio.test._
import zio.test.Assertion._
import pd2.ui.{Completed, Failed, InProgress, ItemState, Pending, PercentageBar, ProgressBar, ProgressBarLayout, WorkItemsBar}
import zio.test.DefaultRunnableSpec

import scala.collection.immutable.SortedMap

object ProgressBarSuite extends DefaultRunnableSpec {
  def spec = {
    suite ("ProgressBar Suite") (

      suite ("PercentageBar") (

        test("0%") {
          val bar = PercentageBar(0, 100, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [          ]   0%"))
        },

        test("33%") {
          val bar = PercentageBar(33, 100, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [===       ]  33%"))
        },

        test("50%") {
          val bar = PercentageBar(50, 100, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [=====     ]  50%"))
        },

        test("100%") {
          val bar = PercentageBar(100, 100, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [==========] 100%"))
        },

        test("short label") {
          val bar = PercentageBar(100, 100, ProgressBarLayout("123", 6, 10))
          assert(ProgressBar.render(bar))(equalTo("123    [==========] 100%"))
        },

        test("long label") {
          val bar = PercentageBar(100, 100, ProgressBarLayout("1234567", 6, 10))
          assert(ProgressBar.render(bar))(equalTo("123456 [==========] 100%"))
        },
      ),

      suite("WorkItemsBar") (

        test("0%") {

          val itemStates = Vector.from(
            (1 to 10).map(_ => Pending))

          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [          ]   0%"))
        },

        test("100% Completed") {
          val itemStates = Vector.from(
            (1 to 10).map(_ => Completed))

          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [==========] 100%"))
        },

        test("50% Completed, 50% Failed") {
          val itemStates = Vector.from (
              (1 to 5)  .map(_ => Completed) ++
              (6 to 10) .map(_ => Failed:ItemState)
            )
          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [=====!!!!!] 100%"))
        },

        test("50% Completed, 50% InProgress") {
          val itemStates = Vector (InProgress, Completed, InProgress, Completed, InProgress)
          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [||==||==||]  40%"))
        },

        test("33% Completed, 33% InProgress, 33% Failed") {
          val itemStates = Vector (InProgress, Completed, Failed)
          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 10))
          assert(ProgressBar.render(bar))(equalTo("test  [||||===!!!]  67%"))
        },

        test("tick1") {
          val itemStates = Vector (InProgress)
          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 3))
          assert(ProgressBar.render(bar, 1))(equalTo("test  [///]   0%"))
        },

        test("tick2") {
          val itemStates = Vector (InProgress)
          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 3))
          assert(ProgressBar.render(bar, 2))(equalTo("test  [---]   0%"))
        },

        test("tick3") {
          val itemStates = Vector (InProgress)
          val bar = WorkItemsBar(itemStates, ProgressBarLayout("test", 5, 3))
          assert(ProgressBar.render(bar, 3))(equalTo("test  [\\\\\\]   0%"))
        },
      )
    )
  }
}
