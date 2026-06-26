package todo.user.domain

sealed trait AuthenticationResult

object AuthenticationResult {
  case object Success extends AuthenticationResult
  final case class Failure(message: String) extends AuthenticationResult
}
