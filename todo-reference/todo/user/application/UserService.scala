package todo.user.application

import scala.concurrent.Future

import todo.shared.domain.DomainError
import todo.user.domain.{AuthenticationResult, User}

trait UserService {
  def list(): Future[Seq[User]]
  def get(id: Long): Future[Option[User]]
  def create(email: String, password: String): Future[Either[DomainError, User]]
  def changeEmail(id: Long, email: String): Future[Either[DomainError, User]]
  def changePassword(id: Long, password: String): Future[Either[DomainError, User]]
  def delete(id: Long): Future[Either[DomainError, User]]
  def register(email: String, password: String): Future[Either[DomainError, User]]
  def login(email: String, password: String): Future[Either[DomainError, User]]
  def authenticate(email: String, password: String): Future[AuthenticationResult]
}
