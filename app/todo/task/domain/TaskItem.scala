package todo.task.domain

import java.time.{Instant, LocalDate}

import todo.category.domain.Category
import todo.shared.domain.AuditInfo
import todo.shared.domain.AuditableEntity
import todo.shared.domain.DomainError
import todo.shared.domain.Priority

final case class TaskItem(
    id: Long,
    title: String,
    description: Option[String],
    urgency: Urgency,
    completedAt: Option[Instant],
    isCompleted: Boolean,
    userId: Option[Long],
    audit: AuditInfo
) extends AuditableEntity {

  def priority: Priority = urgency.priority
  def dueDate: Option[LocalDate] = urgency.dueDate

  def edit(
      newTitle: String,
      newDescription: Option[String],
      newPriority: Priority,
      newDueDate: Option[LocalDate]
  ): Either[DomainError, TaskItem] =
    for {
      _       <- if (TaskItem.isBlank(newTitle)) Left(DomainError.EmptyTitle) else Right(())
      urgency <- Urgency.from(newPriority, newDueDate)
    } yield copy(
      title = newTitle.trim,
      description = TaskItem.normalizeDescription(newDescription),
      urgency = urgency
    )

  def complete(today: LocalDate, now: Instant): Either[DomainError, TaskItem] =
    if (isCompleted) Right(this)
    else if (dueDate.exists(_.isBefore(today))) Left(DomainError.TaskPastDueCannotComplete)
    else Right(copy(isCompleted = true, completedAt = Some(now)))

  def reopen(): TaskItem =
    if (!isCompleted) this
    else copy(isCompleted = false, completedAt = None)

  def assignToCategory(
      category: Category,
      existingLinks: Seq[TaskItemCategory],
      now: Instant,
      by: String
  ): Either[DomainError, Option[TaskItemCategory]] =
    if (category.isDeleted) Left(DomainError.CategoryDeleted)
    else if (existingLinks.exists(l => l.categoryId == category.id && !l.isDeleted)) Right(None)
    else TaskItemCategory.create(this.id, category.id, now, by).map(Some(_))

  def removeFromCategory(
      categoryId: Long,
      existingLinks: Seq[TaskItemCategory],
      now: Instant,
      by: String
  ): Option[TaskItemCategory] =
    existingLinks
      .find(l => l.categoryId == categoryId && !l.isDeleted)
      .map(_.markDeleted(now, by))

  def softDeleteWithUser(deletedBy: String, now: Instant): TaskItem =
    copy(audit = audit.deleted(now, deletedBy), userId = None)

  def restoreWithUser(newUserId: Long): Either[DomainError, TaskItem] =
    if (newUserId > 0) Right(copy(audit = audit.restore(), userId = Some(newUserId)))
    else Left(DomainError.InvalidUserId)

  def markUpdated(now: Instant, by: String): TaskItem = copy(audit = audit.updated(now, by))
}

object TaskItem {

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
      _       <- if (isBlank(title)) Left(DomainError.EmptyTitle) else Right(())
      _       <- if (userId > 0) Right(()) else Left(DomainError.InvalidUserId)
      urgency <- Urgency.from(priority, dueDate)
    } yield TaskItem(
      id = 0L,
      title = title.trim,
      description = normalizeDescription(description),
      urgency = urgency,
      completedAt = None,
      isCompleted = false,
      userId = Some(userId),
      audit = AuditInfo.create(now, by)
    )

  private def isBlank(s: String): Boolean = s == null || s.trim.isEmpty

  private def normalizeDescription(d: Option[String]): Option[String] =
    d.map(_.trim).filter(_.nonEmpty)
}
