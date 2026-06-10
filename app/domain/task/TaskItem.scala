package domain.task

import java.time.{Instant, LocalDate}

import domain.category.Category
import domain.common.{AuditInfo, AuditableEntity, DomainError, Priority}

/**
 * Domain'in cekirdek entity'si; en zengin kural setine sahiptir.
 *
 * TARIH SEMANTIGI:
 *   - `dueDate` gun bazlidir (`LocalDate`): "son tarihi gecmis" karsilastirmasi
 *     gun seviyesinde yapilir; saat onemsizdir.
 *   - `completedAt` ise kesin bir an oldugundan `Instant`'tir.
 *
 * SAFLIK: Hicbir metot `now`/`today` uretmez; bunlar disaridan (servisten) gelir.
 *
 * Kategorilerle iliski (TaskItemCategory) entity icinde TASINMAZ; immutability ve
 * persistence ayrimi geregi davranis metotlari mevcut iliskileri parametre alir
 * ve persist edilecek yeni/guncel iliskiyi DONER.
 */
final case class TaskItem(
    id: Long,
    title: String,
    description: Option[String],
    priority: Priority,
    dueDate: Option[LocalDate],
    completedAt: Option[Instant],
    isCompleted: Boolean,
    userId: Option[Long],
    audit: AuditInfo
) extends AuditableEntity {

  /**
   * Tum duzenlenebilir alanlari TEK SEFERDE degistirir (form-tabanli guncelleme).
   *
   * Alanlari tek tek degistirmek yerine NIHAI durumu dogrularız; boylece sira
   * bagimliligi olusmaz (orn. Low->High'a gecerken yeni dueDate'i de veriyorsak,
   * ara/eski state uzerinden bakan bir kontrol yanlislikla reddetmez).
   * Kural: title bos olamaz, `High ⇒ dueDate`. Kimlik, sahiplik (userId),
   * tamamlanma durumu ve audit korunur.
   */
  def edit(
      newTitle: String,
      newDescription: Option[String],
      newPriority: Priority,
      newDueDate: Option[LocalDate]
  ): Either[DomainError, TaskItem] =
    for {
      _ <- if (TaskItem.isBlank(newTitle)) Left(DomainError.EmptyTitle) else Right(())
      _ <- TaskItem.requireDueDateForHigh(newPriority, newDueDate)
    } yield copy(
      title = newTitle.trim,
      description = TaskItem.normalizeDescription(newDescription),
      priority = newPriority,
      dueDate = newDueDate
    )

  /**
   * `Complete`:
   *   - Zaten tamamlanmissa idempotent (hicbir sey yapmaz, hata da vermez).
   *   - Son tarihi gecmis (`dueDate < today`) gorev tamamlanamaz.
   *   - Aksi halde tamamlanir.
   */
  def complete(today: LocalDate, now: Instant): Either[DomainError, TaskItem] =
    if (isCompleted) Right(this)
    else if (dueDate.exists(_.isBefore(today))) Left(DomainError.TaskPastDueCannotComplete)
    else Right(copy(isCompleted = true, completedAt = Some(now)))

  /** `Reopen`: zaten acik ise idempotent; aksi halde acar. */
  def reopen(): TaskItem =
    if (!isCompleted) this
    else copy(isCompleted = false, completedAt = None)

  /**
   * `AssignToCategory`:
   *   - Silinmis kategoriye atama yapilamaz.
   *   - Aktif (silinmemis) iliski zaten varsa idempotent: `Right(None)` (eklenmez).
   *   - Aksi halde persist edilecek yeni iliskiyi `Right(Some(...))` ile doner.
   */
  def assignToCategory(
      category: Category,
      existingLinks: Seq[TaskItemCategory],
      now: Instant,
      by: String
  ): Either[DomainError, Option[TaskItemCategory]] =
    if (category.isDeleted) Left(DomainError.CategoryDeleted)
    else if (existingLinks.exists(l => l.categoryId == category.id && !l.isDeleted)) Right(None)
    else TaskItemCategory.create(this.id, category.id, now, by).map(Some(_))

  /**
   * `RemoveFromCategory`: ilgili silinmemis iliskiyi bulup soft-delete edilmis
   * kopyasini doner (persist cagiranin isi). Iliski yoksa None (sessizce gecer).
   */
  def removeFromCategory(
      categoryId: Long,
      existingLinks: Seq[TaskItemCategory],
      now: Instant,
      by: String
  ): Option[TaskItemCategory] =
    existingLinks
      .find(l => l.categoryId == categoryId && !l.isDeleted)
      .map(_.markDeleted(now, by))

  /**
   * `SoftDeleteWithUser`: soft-delete + audit + `userId = None`.
   * Mantik: kullanici bagi koptugunda gorevin tek basina anlami kalmaz.
   */
  def softDeleteWithUser(deletedBy: String, now: Instant): TaskItem =
    copy(audit = audit.deleted(now, deletedBy), userId = None)

  /** `RestoreWithUser`: `userId > 0` olmali; restore + tekrar sahiplendir. */
  def restoreWithUser(newUserId: Long): Either[DomainError, TaskItem] =
    if (newUserId > 0) Right(copy(audit = audit.restore(), userId = Some(newUserId)))
    else Left(DomainError.InvalidUserId)

  // --- Audit forward'lari ---
  def markUpdated(now: Instant, by: String): TaskItem = copy(audit = audit.updated(now, by))
}

object TaskItem {

  /**
   * Smart constructor (DOMAIN-SPEC: `new TaskItem(title, description, priority, dueDate, userId)`).
   * Invariant'lar: title bos olamaz, `userId > 0`, `High ⇒ dueDate zorunlu`.
   * Yaratimda: `isCompleted = false`, `completedAt = None`. Transient `id = 0`.
   */
  def create(
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate],
      userId: Long,
      now: Instant,
      by: String
  ): Either[DomainError, TaskItem] =
    for {
      _ <- if (isBlank(title)) Left(DomainError.EmptyTitle) else Right(())
      _ <- if (userId > 0) Right(()) else Left(DomainError.InvalidUserId)
      _ <- requireDueDateForHigh(priority, dueDate)
    } yield TaskItem(
      id = 0L,
      title = title.trim,
      description = normalizeDescription(description),
      priority = priority,
      dueDate = dueDate,
      completedAt = None,
      isCompleted = false,
      userId = Some(userId),
      audit = AuditInfo.create(now, by)
    )

  private def isBlank(s: String): Boolean = s == null || s.trim.isEmpty

  /** Trim'ler; bos/whitespace string'i None'a indirger. */
  private def normalizeDescription(d: Option[String]): Option[String] =
    d.map(_.trim).filter(_.nonEmpty)

  /**
   * Capraz alan invariant'i (TEK KAYNAK): `High ⇒ dueDate zorunlu`.
   * Hem `create` hem `edit` ayni kurali buradan dogrular; kural degisirse
   * yalnizca burayi guncellemek yeter.
   */
  private def requireDueDateForHigh(
      priority: Priority,
      dueDate: Option[LocalDate]
  ): Either[DomainError, Unit] =
    if (priority == Priority.High && dueDate.isEmpty) Left(DomainError.HighPriorityRequiresDueDate)
    else Right(())
}
