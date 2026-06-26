package todo.task.domain

import java.time.Instant

import todo.shared.domain.AuditInfo
import todo.shared.domain.AuditableEntity
import todo.shared.domain.DomainError

final case class TaskItemCategory(
    id: Long,
    taskItemId: Long,
    categoryId: Long,
    audit: AuditInfo
) extends AuditableEntity {

  def softDelete(): TaskItemCategory = copy(audit = audit.softDelete())
  def markDeleted(now: Instant, by: String): TaskItemCategory = copy(audit = audit.deleted(now, by))
  def restore(): TaskItemCategory = copy(audit = audit.restore())
}

object TaskItemCategory {

  def create(
      taskItemId: Long,
      categoryId: Long,
      now: Instant,
      by: String
  ): Either[DomainError, TaskItemCategory] =
    for {
      _ <- require(taskItemId, DomainError.InvalidTaskItemId)
      _ <- require(categoryId, DomainError.InvalidCategoryId)
    } yield TaskItemCategory(
      id = 0L,
      taskItemId = taskItemId,
      categoryId = categoryId,
      audit = AuditInfo.create(now, by)
    )

  private def require(fk: Long, err: DomainError): Either[DomainError, Long] =
    if (fk > 0) Right(fk) else Left(err)
}
