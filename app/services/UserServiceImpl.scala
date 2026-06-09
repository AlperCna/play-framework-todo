package services

import javax.inject.{Inject, Singleton}

import domain.auth.AuthenticationResult
import domain.common.DomainError
import domain.user.User
import repositories.UserRepository

/** [[UserService]]'in implementasyonu. */
@Singleton
class UserServiceImpl @Inject() (userRepo: UserRepository, clock: Clock) extends UserService {

  private val AuditUser = "system"

  override def list(): Seq[User] = userRepo.list()

  override def get(id: Long): Option[User] = userRepo.get(id)

  override def create(email: String, password: String): Either[DomainError, User] =
    User.create(email, password, clock.now, AuditUser).map(userRepo.add)

  override def changeEmail(id: Long, email: String): Either[DomainError, User] =
    for {
      user <- found(id)
      changed <- user.changeEmail(email)
      saved <- persist(changed.markUpdated(clock.now, AuditUser))
    } yield saved

  override def changePassword(id: Long, password: String): Either[DomainError, User] =
    for {
      user <- found(id)
      changed <- user.changePassword(password)
      saved <- persist(changed.markUpdated(clock.now, AuditUser))
    } yield saved

  override def delete(id: Long): Either[DomainError, User] =
    for {
      user <- found(id)
      saved <- persist(user.markDeleted(clock.now, AuditUser))
    } yield saved

  override def authenticate(email: String, password: String): AuthenticationResult =
    userRepo.findByEmail(email) match {
      case Some(user) if user.passwordMatches(password) => AuthenticationResult.Success
      case _ => AuthenticationResult.Failure("Invalid email or password.")
    }

  private def found(id: Long): Either[DomainError, User] =
    userRepo.get(id).toRight(DomainError.NotFound("User", id))

  private def persist(user: User): Either[DomainError, User] =
    userRepo.update(user).toRight(DomainError.NotFound("User", user.id))
}
