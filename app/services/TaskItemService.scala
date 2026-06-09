package services

import java.time.LocalDate

import domain.category.Category
import domain.common.{DomainError, Priority}
import domain.task.{TaskItem, TaskItemCategory}

/**
 * TaskItem uygulama (application) servisi — orkestrasyon katmani.
 *
 * Controller bu arayuze baglanir (repository'ye DEGIL). Gorevi: repository'den
 * yukle -> domain davranisini cagir -> persist et -> `Either[DomainError, T]` don.
 * `now`/`today`/audit `by` gibi yan etkiler bu katmanda toplanir ve domain'e
 * parametre olarak gecirilir; boylece domain saf kalir.
 */
trait TaskItemService {

  def list(): Seq[TaskItem]

  /** Bir kullanicinin silinmemis gorevleri (CurrentUser'a gore listeleme). */
  def listByUser(userId: Long): Seq[TaskItem]

  def get(id: Long): Option[TaskItem]

  def create(
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate],
      userId: Long
  ): Either[DomainError, TaskItem]

  def update(
      id: Long,
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate]
  ): Either[DomainError, TaskItem]

  def complete(id: Long): Either[DomainError, TaskItem]

  def reopen(id: Long): Either[DomainError, TaskItem]

  def delete(id: Long): Either[DomainError, TaskItem]

  /**
   * Gorevi bir kategoriye atar. Yeni iliski olustuysa `Some`, idempotent (zaten
   * atanmis) ise `None` doner. Silinmis kategoride `Left(CategoryDeleted)`.
   */
  def assignToCategory(taskId: Long, categoryId: Long): Either[DomainError, Option[TaskItemCategory]]

  /**
   * Gorevi bir kategoriden cikarir (ilgili aktif iliskiyi soft-delete eder).
   * Iliski yoksa sessizce gecer (idempotent).
   */
  def removeFromCategory(taskId: Long, categoryId: Long): Either[DomainError, Unit]

  /** Gorevin atanmis (aktif, silinmemis) kategorileri. */
  def categoriesOf(taskId: Long): Seq[Category]
}
