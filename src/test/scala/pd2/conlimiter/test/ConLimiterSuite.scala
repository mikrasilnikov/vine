package pd2.conlimiter.test

import pd2.conlimiter.{ConnectionsLimiter, ConnectionsLimiterLive}
import sttp.model.Uri
import zio._
import zio.clock.Clock
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test._
import zio.test.environment.TestClock

import scala.collection.immutable

object ConLimiterSuite  extends DefaultRunnableSpec {

  def completeAndSleep(promise: Promise[Nothing, Unit]): ZIO[Clock, Nothing, Nothing] =
    promise.succeed() *> ZIO.sleep(1.second).forever

  def spec =

    suite("ConLimiterSuite")(
      testM("Permits one") {

        val globalLimit = 1
        val domainLimit = 1

        for {
          globalSem <- Semaphore.make(globalLimit)
          stateRef  <- RefM.make(immutable.HashMap[String, Semaphore]())
          limiter = ConnectionsLimiterLive(globalSem, domainLimit, stateRef)

          domainSem <- limiter.getDomainSemaphore(Uri.unsafeParse("https://www.traxsource.com"))
          actual <- globalSem.available zip domainSem.available
          expected = (1L, 1L)
        } yield assert(actual)(equalTo(expected))
      },

      testM("Blocks one for same domain") {

        val globalLimit = 1
        val domainLimit = 1

        for {
          globalSem <- Semaphore.make(globalLimit)
          stateRef  <- RefM.make(immutable.HashMap[String, Semaphore]())
          limiter = ConnectionsLimiterLive(globalSem, domainLimit, stateRef)

          uri1 = Uri.unsafeParse("https://www.traxsource.com/just-added")
          uri2 = Uri.unsafeParse("http://traxsource.com/house")

          p1 <- Promise.make[Nothing, Unit]
          _ <- limiter.withPermit(uri1)(completeAndSleep(p1)).fork *> p1.await

          domainSem <- limiter.getDomainSemaphore(uri2)
          actual <- globalSem.available zip domainSem.available
          expected = (0L, 0L)

        } yield assert(actual)(equalTo(expected))
      },

      testM("Releases one for same domain") {

        val globalLimit = 1
        val domainLimit = 1

        for {
          globalSem <- Semaphore.make(globalLimit)
          stateRef  <- RefM.make(immutable.HashMap[String, Semaphore]())
          limiter = ConnectionsLimiterLive(globalSem, domainLimit, stateRef)

          uri1 = Uri.unsafeParse("https://www.traxsource.com/just-added")
          uri2 = Uri.unsafeParse("http://traxsource.com/house")

          p1 <- Promise.make[Nothing, Unit]
          _ <- limiter.withPermit(uri1)(p1.succeed() *> ZIO.sleep(1.second)).fork *> p1.await

          _ <- TestClock.adjust(1.second)

          domainSem <- limiter.getDomainSemaphore(uri2)
          actual <- globalSem.available zip domainSem.available
          expected = (1L, 1L)

        } yield assert(actual)(equalTo(expected))
      },

      testM("Permits for different domains") {

        val globalLimit = 2
        val domainLimit = 1

        for {
          globalSem <- Semaphore.make(globalLimit)
          stateRef  <- RefM.make(immutable.HashMap[String, Semaphore]())
          limiter = ConnectionsLimiterLive(globalSem, domainLimit, stateRef)

          uri1 = Uri.unsafeParse("https://www.traxsource.com/just-added")
          uri2 = Uri.unsafeParse("http://beatport.com/house")

          p1 <- Promise.make[Nothing, Unit]
          _ <- limiter.withPermit(uri1)(completeAndSleep(p1)).fork *> p1.await

          domainSem <- limiter.getDomainSemaphore(uri2)
          actual <- globalSem.available zip domainSem.available
          expected = (1L, 1L)

        } yield assert(actual)(equalTo(expected))
      },

      testM("Ensures global limit") {

        val globalLimit = 2
        val domainLimit = 1

        for {
          globalSem <- Semaphore.make(globalLimit)
          stateRef  <- RefM.make(immutable.HashMap[String, Semaphore]())
          limiter = ConnectionsLimiterLive(globalSem, domainLimit, stateRef)

          uri1 = Uri.unsafeParse("https://www.traxsource.com/just-added")
          uri2 = Uri.unsafeParse("http://beatport.com/house")
          uri3 = Uri.unsafeParse("http://www.junodownload.com/house")

          p1 <- Promise.make[Nothing, Unit]
          p2 <- Promise.make[Nothing, Unit]
          _ <- limiter.withPermit(uri1)(completeAndSleep(p1)).fork *> p1.await
          _ <- limiter.withPermit(uri2)(completeAndSleep(p2)).fork *> p2.await

          domainSem <- limiter.getDomainSemaphore(uri3)

          actual <- globalSem.available zip domainSem.available
          expected = (0L, 1L)

        } yield assert(actual)(equalTo(expected))
      },

    )

}
