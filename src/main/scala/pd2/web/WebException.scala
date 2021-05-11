package pd2.web

sealed trait WebException extends Throwable
case class RecoverableWebException(msg : String) extends WebException
case class FatalWebException(msg : String) extends WebException