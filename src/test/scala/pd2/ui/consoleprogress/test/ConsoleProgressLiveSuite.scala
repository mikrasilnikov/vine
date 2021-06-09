package pd2.ui.consoleprogress.test

import zio._
import zio.console._
import zio.test._
import zio.test.Assertion._
import pd2.ui.consoleprogress._
import pd2.ui._
import pd2.ui.consoleprogress.ConsoleProgressLive.DrawState

object ConsoleProgressLiveSuite  extends DefaultRunnableSpec {

  def createService(console : Console.Service) : Task[ConsoleProgressLive] =
    for {
      barRef <- RefM.make(Vector[BucketProgressBar]())
      stateRef <- RefM.make(DrawState(firstFrame = true))
      dimensions = ProgressBarDimensions(5, 10)
    } yield ConsoleProgressLive(console, barRef, stateRef, dimensions, runningInsideIntellij = false)

  def spec =

    suite("ConsoleProgressLiveSuite")(

      testM("InitializeBar - new bar") {
        for {
          s <- ZIO.service[Console.Service].flatMap(createService)
          _ <- s.initializeBar("test1", List(10, 20))
          _ <- s.initializeBar("test2", List(30, 40))
          bars <- s.progressBarsRef.get
          expected = Vector(ProgressBucket(30, 0, 0), ProgressBucket(40, 0, 0))
        } yield
            assert(bars.length)(equalTo(2)) &&
            assert(bars.last.buckets)(equalTo(expected))

      },

      testM("InitializeBar - existing bar") {
        for {
          s <- ZIO.service[Console.Service].flatMap(createService)
          _ <- s.initializeBar("test", List(10, 20))
          _ <- s.initializeBar("test", List(30, 40))
          bars <- s.progressBarsRef.get
          expected = Vector(ProgressBucket(30, 0, 0), ProgressBucket(40, 0, 0))
        } yield
          assert(bars.length)(equalTo(1)) &&
            assert(bars.head.buckets)(equalTo(expected))
      },

      testM("CompleteOne") {
        for {
          s     <- ZIO.service[Console.Service].flatMap(createService)
          refs  <- s.initializeBar("test", List(10, 20))
          _     <- s.completeOne(refs(0))
          _     <- s.completeOne(refs(1))
          bars  <- s.progressBarsRef.get
          expected = Vector(ProgressBucket(10, 1, 0), ProgressBucket(20, 1, 0))
        } yield
          assert(bars.length)(equalTo(1)) &&
            assert(bars.head.buckets)(equalTo(expected))
      },

      testM("FailOne") {
        for {
          s     <- ZIO.service[Console.Service].flatMap(createService)
          refs  <- s.initializeBar("test", List(10, 20))
          _     <- s.failOne(refs(0))
          _     <- s.failOne(refs(1))
          bars  <- s.progressBarsRef.get
          expected = Vector(ProgressBucket(10, 0, 1), ProgressBucket(20, 0, 1))
        } yield
          assert(bars.length)(equalTo(1)) &&
            assert(bars.head.buckets)(equalTo(expected))
      },

      testM("CompleteBar") {
        for {
          s     <- ZIO.service[Console.Service].flatMap(createService)
          refs  <- s.initializeBar("test", List(10, 20))
          _     <- s.completeOne(refs(0))
          _     <- s.failOne(refs(1))
          _     <- s.completeBar("test")
          bars  <- s.progressBarsRef.get
          expected = Vector(ProgressBucket(10, 10, 0), ProgressBucket(20, 19, 1))
        } yield
          assert(bars.length)(equalTo(1)) &&
            assert(bars.head.buckets)(equalTo(expected))
      },
    )
}
