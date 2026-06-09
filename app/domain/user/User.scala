package domain.user

import java.time.Instant

import domain.common.{AuditInfo, AuditableEntity, DomainError}

/**
 * Kullanici entity'si.
 *
 * NOT (DOMAIN-SPEC): Bu projede parola HASH'lenmez; tek bir `password` alaninda
 * duz metin (plain text) tutulur.
 *
 * Navigation koleksiyonlari (TaskItems/Categories) domain'de tasinmaz; iliskiler
 * repository sorgulariyla cozulur (spec'te bunlar yalnizca bilgi amacli).
 */
final case class User(
    id: Long,
    email: String,
    password: String,
    audit: AuditInfo
) extends AuditableEntity {

  /** `ChangeEmail`: bos olamaz; aksi halde trim'leyip atar. */
  def changeEmail(newEmail: String): Either[DomainError, User] =
    User.requireNonBlank(newEmail, DomainError.EmptyEmail).map(e => copy(email = e.trim))

  /** `ChangePassword`: bos olamaz; aksi halde atar. */
  def changePassword(newPassword: String): Either[DomainError, User] =
    if (newPassword == null || newPassword.trim.isEmpty) Left(DomainError.EmptyPassword)
    else Right(copy(password = newPassword))

  /** Plain-text parola dogrulamasi (duz karsilastirma). */
  def passwordMatches(candidate: String): Boolean =
    password == candidate

  // --- Audit forward'lari (immutable: yeni kopya doner) ---
  def markUpdated(now: Instant, by: String): User = copy(audit = audit.updated(now, by))
  def markDeleted(now: Instant, by: String): User = copy(audit = audit.deleted(now, by))
  def restore(): User = copy(audit = audit.restore())
}

object User {

  /**
   * Smart constructor (DOMAIN-SPEC: `Create(email, password)`).
   * Invariant'lar yaratimda dogrulanir; gecersiz bir User asla var olamaz.
   * Yeni entity transient'tir: `id = 0`; gercek id'yi repository atar.
   */
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
