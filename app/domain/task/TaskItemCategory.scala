package domain.task

import java.time.Instant

import domain.common.{AuditInfo, AuditableEntity, DomainError}

/**
 * TaskItem <-> Category coka-coka (N-N) iliskisini tasiyan ara (join) entity.
 *
 * Kendisi de [[AuditableEntity]] oldugundan soft-delete ve audit bilgisi tutar:
 * iliskiyi "silmek" = soft delete.
 */
final case class TaskItemCategory(
    id: Long,
    taskItemId: Long,
    categoryId: Long,
    audit: AuditInfo
) extends AuditableEntity {

  // --- Audit forward'lari ---
  def softDelete(): TaskItemCategory = copy(audit = audit.softDelete())
  def markDeleted(now: Instant, by: String): TaskItemCategory = copy(audit = audit.deleted(now, by))
  def restore(): TaskItemCategory = copy(audit = audit.restore())
}

object TaskItemCategory {

  /**
   * Smart constructor. Iki FK de `> 0` olmali. Yeni iliski transient (`id = 0`).
   */
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
