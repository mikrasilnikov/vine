package pd2

import io.getquill._
import zio.{ExitCode, URIO, ZIO}

object QuillTest extends zio.App {
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal)

    ZIO.succeed().exitCode
  }
}
