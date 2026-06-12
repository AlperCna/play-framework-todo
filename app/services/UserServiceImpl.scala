package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import domain.auth.AuthenticationResult
import domain.common.DomainError
import domain.user.User
import repositories.interfaces.UserRepository

/** [[UserService]]'in implementasyonu. */
@Singleton
class UserServiceImpl @Inject() (userRepo: UserRepository, clock: Clock)(
    implicit ec: ExecutionContext
) extends UserService {

  private val AuditUser = "system"

  override def list(): Future[Seq[User]] = userRepo.list()

  override def get(id: Long): Future[Option[User]] = userRepo.get(id)

  override def create(email: String, password: String): Future[Either[DomainError, User]] =
    ServiceResult
      .fromEither(User.create(email, password, clock.now, AuditUser))
      .flatMap(user => ServiceResult.fromFuture(userRepo.add(user)))
      .value

  override def register(email: String, password: String): Future[Either[DomainError, User]] =
    userRepo.findByEmail(email.trim).flatMap {
      case Some(_) => Future.successful(Left(DomainError.EmailAlreadyTaken))
      case None =>
        ServiceResult
          .fromEither(User.create(email, password, clock.now, AuditUser))
          .flatMap(user => ServiceResult.fromFuture(userRepo.add(user)))
          .value
    }

  override def login(email: String, password: String): Future[Either[DomainError, User]] =
    userRepo.findByEmail(email.trim).map {
      case Some(user) if user.passwordMatches(password) => Right(user)
      case _                                            => Left(DomainError.InvalidCredentials)
    }

  override def changeEmail(id: Long, email: String): Future[Either[DomainError, User]] =
    (for {
      user    <- found(id)
      changed <- ServiceResult.fromEither(user.changeEmail(email))
      saved   <- persist(changed.markUpdated(clock.now, AuditUser))
    } yield saved).value

  override def changePassword(id: Long, password: String): Future[Either[DomainError, User]] =
    (for {
      user    <- found(id)
      changed <- ServiceResult.fromEither(user.changePassword(password))
      saved   <- persist(changed.markUpdated(clock.now, AuditUser))
    } yield saved).value

  override def delete(id: Long): Future[Either[DomainError, User]] =
    (for {
      user  <- found(id)
      saved <- persist(user.markDeleted(clock.now, AuditUser))
    } yield saved).value

  override def authenticate(email: String, password: String): Future[AuthenticationResult] =
    userRepo.findByEmail(email).map {
      case Some(user) if user.passwordMatches(password) => AuthenticationResult.Success
      case _ => AuthenticationResult.Failure("Invalid email or password.")
    }

  private def found(id: Long): ServiceResult[User] =
    ServiceResult.fromOptionF(userRepo.get(id), DomainError.NotFound("User", id))

  private def persist(user: User): ServiceResult[User] =
    ServiceResult.fromOptionF(userRepo.update(user), DomainError.NotFound("User", user.id))
}
