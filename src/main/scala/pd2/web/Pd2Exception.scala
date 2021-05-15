package pd2.web

sealed trait Pd2Exception extends Throwable {
  val message : String
  val fatal : Boolean
}

object Pd2Exception {
  case class UnexpectedServiceResponse(message: String, parserInput: String, inner: Option[Throwable])
    extends Pd2Exception
  {
    override val fatal: Boolean = true
    override def toString: String = {
      val sb = new StringBuilder
      sb.addAll(message)
      sb.addOne('\n')
      sb.addAll(parserInput)
      inner.fold(()) { t => sb.addOne('\n'); sb.addAll(t.toString) }
      sb.mkString
    }
  }

  case class ServiceUnavailable(message: String, inner: Option[Throwable]) extends Pd2Exception {
    override val fatal: Boolean = false
  }

  case class InternalConfigurationError(message: String) extends Pd2Exception {
    override val fatal: Boolean = true
  }
}

