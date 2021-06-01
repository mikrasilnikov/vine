package pd2.ui.test

import pd2.ui.ProgressBar.{Completed, Failed, InProgress, ItemState, Pending, ProgressBarDimensions, ProgressBarLayout}
import zio.test._
import zio.test.Assertion._
import pd2.ui.{ProgressBar}
import zio.test.DefaultRunnableSpec

import scala.collection.mutable.ArrayBuffer

object ProgressBarSuite extends DefaultRunnableSpec {
  def spec = {
    suite ("ProgressBar Suite") (

      suite("WorkItemsBar") (

        test("empty bar") {

          val itemStates = ArrayBuffer[ItemState]()

          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))
          assert(ProgressBar.render(bar))(equalTo("test  [          ]   0%"))
        },

        test("0%") {

          val itemStates = ArrayBuffer.from[ItemState](
            (1 to 10).map(_ => Pending))

          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))
          assert(ProgressBar.render(bar))(equalTo("test  [          ]   0%"))
        },

        test("100% Completed") {
          val itemStates = ArrayBuffer.from[ItemState](
            (1 to 10).map(_ => Completed))

          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))
          assert(ProgressBar.render(bar))(equalTo("test  [==========] 100%"))
        },

        test("50% Completed, 50% Failed") {
          val itemStates = ArrayBuffer.from[ItemState](
              (1 to 5)  .map(_ => Completed) ++
              (6 to 10) .map(_ => Failed:ItemState)
            )
          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))
          assert(ProgressBar.render(bar))(equalTo("test  [=====!!!!!] 100%"))
        },

        test("50% Completed, 50% InProgress") {
          val itemStates = ArrayBuffer[ItemState] (InProgress, Completed, InProgress, Completed, InProgress)
          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))
          assert(ProgressBar.render(bar))(equalTo("test  [||==||==||]  40%"))
        },

        test("33% Completed, 33% InProgress, 33% Failed") {
          val itemStates = ArrayBuffer[ItemState] (InProgress, Completed, Failed)
          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 10)))
          assert(ProgressBar.render(bar))(equalTo("test  [||||===!!!]  67%"))
        },

        test("tick1") {
          val itemStates = ArrayBuffer[ItemState] (InProgress)
          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 3)))
          assert(ProgressBar.render(bar, 1))(equalTo("test  [///]   0%"))
        },

        test("tick2") {
          val itemStates = ArrayBuffer[ItemState](InProgress)
          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 3)))
          assert(ProgressBar.render(bar, 2))(equalTo("test  [---]   0%"))
        },

        test("tick3") {
          val itemStates = ArrayBuffer[ItemState] (InProgress)
          val bar = ProgressBar(itemStates, ProgressBarLayout("test", ProgressBarDimensions(5, 3)))
          assert(ProgressBar.render(bar, 3))(equalTo("test  [\\\\\\]   0%"))
        },
      )
    )
  }
}
