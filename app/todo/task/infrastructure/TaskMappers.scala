package todo.task.infrastructure

import java.sql.{Date => SqlDate}

import todo.shared.infrastructure.AuditMapper
import todo.shared.domain.DomainError
import todo.shared.domain.Priority
import todo.shared.infrastructure.RowMapper
import todo.task.domain.{TaskItem, TaskItemCategory, Urgency}

object TaskMappers {

  implicit val taskItemMapper: RowMapper[TaskItem, TaskRow] = new RowMapper[TaskItem, TaskRow] {

    def toRow(t: TaskItem): TaskRow =
      TaskRow(
        t.id, t.title, t.description,
        t.priority.value, t.dueDate.map(d => SqlDate.valueOf(d)),
        t.isCompleted, AuditMapper.tsOpt(t.completedAt), t.userId,
        AuditMapper.ts(t.audit.createdAt), t.audit.createdBy,
        AuditMapper.tsOpt(t.audit.updatedAt), t.audit.updatedBy,
        t.audit.isDeleted, AuditMapper.tsOpt(t.audit.deletedAt), t.audit.deletedBy
      )

    def toDomain(r: TaskRow): TaskItem =
      Priority
        .fromInt(r.priorityValue)
        .toRight(DomainError.InvalidPriorityValue(r.priorityValue))
        .flatMap(p => Urgency.from(p, r.dueDate.map(_.toLocalDate)))
        .map { urgency =>
          TaskItem(
            r.id, r.title, r.description, urgency,
            r.completedAt.map(_.toInstant), r.isCompleted, r.userId,
            AuditMapper.toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
          )
        }
        .fold(e => sys.error(s"DB invariant ihlali (task id=${r.id}): ${e.message}"), identity)
  }

  implicit val taskItemCategoryMapper: RowMapper[TaskItemCategory, TaskItemCategoryRow] =
    new RowMapper[TaskItemCategory, TaskItemCategoryRow] {

      def toRow(l: TaskItemCategory): TaskItemCategoryRow =
        TaskItemCategoryRow(
          l.id, l.taskItemId, l.categoryId,
          AuditMapper.ts(l.audit.createdAt), l.audit.createdBy,
          AuditMapper.tsOpt(l.audit.updatedAt), l.audit.updatedBy,
          l.audit.isDeleted, AuditMapper.tsOpt(l.audit.deletedAt), l.audit.deletedBy
        )

      def toDomain(r: TaskItemCategoryRow): TaskItemCategory =
        TaskItemCategory(
          r.id, r.taskItemId, r.categoryId,
          AuditMapper.toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
        )
    }
}
