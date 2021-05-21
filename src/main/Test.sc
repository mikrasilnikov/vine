import io.getquill._

val ctx = new SqlMirrorContext(MirrorSqlDialect, Literal)
import ctx._

case class Circle(radius: Float)

val pi = quote(3.14159)

val area = quote {
  (c: Circle) => {
    val r2 = c.radius * c.radius
    pi * r2
  }
}

val areas = quote {
  query[Circle].map(c => area(c))
}

ctx.run(areas)