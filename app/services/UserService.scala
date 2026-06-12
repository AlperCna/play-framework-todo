package services

import scala.concurrent.Future

import domain.auth.AuthenticationResult
import domain.common.DomainError
import domain.user.User

/**
 * User uygulama servisi.
 *
 * Gercek DB ile repo'lar `Future` dondugu icin metotlar `Future` doner.
 */
trait UserService {
  def list(): Future[Seq[User]]
  def get(id: Long): Future[Option[User]]
  def create(email: String, password: String): Future[Either[DomainError, User]]
  def changeEmail(id: Long, email: String): Future[Either[DomainError, User]]
  def changePassword(id: Long, password: String): Future[Either[DomainError, User]]
  def delete(id: Long): Future[Either[DomainError, User]]

  /**
   * Kayit (register): email benzersiz olmali. Email zaten kayitliysa
   * `EmailAlreadyTaken`. Benzersizlik tekil entity invariant'i degil (birden
   * fazla kaydi ilgilendirir), bu yuzden servis katmaninda kontrol edilir.
   */
  def register(email: String, password: String): Future[Either[DomainError, User]]

  /**
   * Giris (login): dogru email+parola ise kullaniciyi doner (session'a koymak
   * icin), aksi halde `InvalidCredentials`.
   */
  def login(email: String, password: String): Future[Either[DomainError, User]]

  /** Plain-text parola ile basit kimlik dogrulama (spec domain modeli). */
  def authenticate(email: String, password: String): Future[AuthenticationResult]
}
