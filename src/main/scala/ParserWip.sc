import cats.parse.{Parser => P}
import cats.parse.Parser.not
import cats.parse.Rfc5234.{char, sp}
import pd2.data.TrackParsing.Single

val pairSep = sp.rep.?.soft ~ P.char(',') ~ sp.rep.?
val coopSep = sp.rep.?.soft ~ P.char('&') ~ sp.rep.?

val pres = P.oneOf(List(P.ignoreCase("presents"), P.ignoreCase("pres."), P.ignoreCase("pres")))
val presSep = sp.rep.soft ~ pres ~ sp.rep

val feat = P.oneOf(List(P.ignoreCase("featuring"), P.ignoreCase("feat."), P.ignoreCase("feat")))
val featSep = sp.rep.soft ~ feat ~ sp.rep

val anySep = presSep.backtrack | featSep.backtrack | pairSep.backtrack | coopSep

val notSp = P.charWhere(c => !Character.isSpaceChar(c))

//val single = (notSp ~ (char.soft <* not(anySep)).rep0 ~ char.?).string.map(Single)

val single = ((char.soft <* not(P.end | anySep)).rep0.with1 ~ char).string.map(Single)

single.parse("hello")
single.parse("hello & world")
single.parse("hello pres world ")
single.parse("A ")
single.parse("A pres B")