package pd2.providers

import sttp.model.Uri

object Exceptions {
  case class UnexpectedServiceResponse(message: String, parserInput: String, inner: Option[Throwable]) extends Throwable
  {
    override def toString: String = {
      val sb = new StringBuilder
      sb.addAll(message)
      sb.addOne('\n')
      sb.addAll(parserInput)
      inner.fold(()) { t => sb.addOne('\n'); sb.addAll(t.toString) }
      sb.mkString
    }
  }

  case class ServiceUnavailable(message: String, uri: Uri, inner: Option[Throwable] = None) extends Throwable {}

  case class BadContentLength(message: String, uri: Uri) extends Throwable {}

  case class InternalConfigurationError(message: String) extends Throwable {}
}

