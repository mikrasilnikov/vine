
import zio._
import zio.console._


val failingEffect = ZIO.fail(new Exception("Boo!"))
val schedule = Schedule.recurs(5).tapOutput(n => putStrLn(s"$n").when(n < 10).orElseSucceed())

Runtime.default.unsafeRun(failingEffect.retry(schedule))
