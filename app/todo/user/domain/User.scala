package todo.user.domain

import java.time.Instant

import todo.shared.domain.AuditInfo
import todo.shared.domain.AuditableEntity
import todo.shared.domain.DomainError

final case class User(
    id: Long,
    email: String,
    password: String,
    audit: AuditInfo
) extends AuditableEntity {

  def changeEmail(newEmail: String): Either[DomainError, User] =
    User.requireNonBlank(newEmail, DomainError.EmptyEmail).map(e => copy(email = e.trim))

  def changePassword(newPassword: String): Either[DomainError, User] =
    if (newPassword == null || newPassword.trim.isEmpty) Left(DomainError.EmptyPassword)
    else Right(copy(password = newPassword))

  def passwordMatches(candidate: String): Boolean =
    password == candidate

  def markUpdated(now: Instant, by: String): User = copy(audit = audit.updated(now, by))
  def markDeleted(now: Instant, by: String): User = copy(audit = audit.deleted(now, by))
  def restore(): User = copy(audit = audit.restore())
}

object User {

  def create(
      email: String,
      password: String,
      now: Instant,
      by: String
  ): Either[DomainError, User] =
    for {
      e <- requireNonBlank(email, DomainError.EmptyEmail)
      p <- requireNonBlank(password, DomainError.EmptyPassword)
    } yield User(
      id = 0L,
      email = e.trim,
      password = p,
      audit = AuditInfo.create(now, by)
    )

  private def requireNonBlank(s: String, err: DomainError): Either[DomainError, String] =
    if (s == null || s.trim.isEmpty) Left(err) else Right(s)
}
