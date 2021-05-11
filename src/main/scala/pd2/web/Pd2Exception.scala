package pd2.web

sealed trait Pd2Exception extends Throwable
case class ParseFailure(msg: String, parserInput: String, inner: Option[Throwable]) extends Pd2Exception
case class RecoverableWebException(msg : String) extends Pd2Exception
case class FatalWebException(msg : String) extends Pd2Exception