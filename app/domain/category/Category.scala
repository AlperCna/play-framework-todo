package domain.category

import java.time.Instant

import domain.common.{AuditInfo, AuditableEntity, DomainError}

/**
 * Kategori entity'si.
 *
 * TaskItem'in aksine, Category'de `description` ZORUNLUDUR.
 */
final case class Category(
    id: Long,
    name: String,
    description: String,
    userId: Long,
    audit: AuditInfo
) extends AuditableEntity {

  /** `Rename`: bos olamaz; aksi halde trim'leyip atar. */
  def rename(newName: String): Either[DomainError, Category] =
    Category.requireNonBlank(newName, DomainError.EmptyCategoryName).map(n => copy(name = n.trim))

  /** `SetDescription`: bos olamaz; aksi halde trim'leyip atar. */
  def setDescription(newDescription: String): Either[DomainError, Category] =
    Category
      .requireNonBlank(newDescription, DomainError.EmptyCategoryDescription)
      .map(d => copy(description = d.trim))

  // --- Audit forward'lari ---
  def markUpdated(now: Instant, by: String): Category = copy(audit = audit.updated(now, by))
  def markDeleted(now: Instant, by: String): Category = copy(audit = audit.deleted(now, by))
  def restore(): Category = copy(audit = audit.restore())
}

object Category {

  /**
   * Smart constructor (DOMAIN-SPEC: `new Category(name, description, userId)`).
   * name + description bos olamaz, `userId > 0`. Yeni entity transient (`id = 0`).
   */
  def create(
      name: String,
      description: String,
      userId: Long,
      now: Instant,
      by: String
  ): Either[DomainError, Category] =
    for {
      n <- requireNonBlank(name, DomainError.EmptyCategoryName)
      d <- requireNonBlank(description, DomainError.EmptyCategoryDescription)
      _ <- requirePositive(userId)
    } yield Category(
      id = 0L,
      name = n.trim,
      description = d.trim,
      userId = userId,
      audit = AuditInfo.create(now, by)
    )

  private def requireNonBlank(s: String, err: DomainError): Either[DomainError, String] =
    if (s == null || s.trim.isEmpty) Left(err) else Right(s)

  private def requirePositive(userId: Long): Either[DomainError, Long] =
    if (userId > 0) Right(userId) else Left(DomainError.InvalidUserId)
}
