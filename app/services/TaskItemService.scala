package services

import java.time.LocalDate

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
   * atanmis) ise `None` doner. (Bu fazda UI yok; servis API'si hazirdir.)
   */
  def assignToCategory(taskId: Long, categoryId: Long): Either[DomainError, Option[TaskItemCategory]]
}
