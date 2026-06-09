package services

import domain.auth.AuthenticationResult
import domain.common.DomainError
import domain.user.User

/**
 * User uygulama servisi.
 *
 * Bu fazda bir controller'i yok; domain+repo katmaniyla ayni simetride yazilir
 * ve sonraki faz (auth/User UI) icin hazirdir.
 */
trait UserService {
  def list(): Seq[User]
  def get(id: Long): Option[User]
  def create(email: String, password: String): Either[DomainError, User]
  def changeEmail(id: Long, email: String): Either[DomainError, User]
  def changePassword(id: Long, password: String): Either[DomainError, User]
  def delete(id: Long): Either[DomainError, User]

  /** Plain-text parola ile basit kimlik dogrulama. */
  def authenticate(email: String, password: String): AuthenticationResult
}
