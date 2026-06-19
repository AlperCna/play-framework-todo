package persistence.db.mappers

import java.sql.{Date => SqlDate}

import domain.common.{DomainError, Priority}
import domain.task.{TaskItem, TaskItemCategory, Urgency}
import persistence.db.AuditMapper._
import persistence.db.{RowMapper, TaskItemCategoryRow, TaskRow}

/**
 * `domain.task` entity'lerinin (TaskItem + TaskItemCategory) Row <-> domain
 * cevrimi. `domain.task` paketini AYNALAR: join entity'si de burada durur.
 *
 * TaskItem OZEL: `urgency` ADT'si `priorityValue` + `dueDate`'e DUZLESIR; okurken
 * `Urgency.from` ile yeniden kurulur. "High + NULL" satiri DB CHECK constraint'i
 * ile zaten imkansiz oldugundan, yine de gelirse veri-butunlugu hatasi olarak
 * `sys.error` ile patlatilir.
 */
object TaskMappers {

  implicit val taskItemMapper: RowMapper[TaskItem, TaskRow] = new RowMapper[TaskItem, TaskRow] {

    def toRow(t: TaskItem): TaskRow =
      TaskRow(
        t.id, t.title, t.description,
        t.priority.value, t.dueDate.map(d => SqlDate.valueOf(d)), // <-- urgency DUZLESIR
        t.isCompleted, tsOpt(t.completedAt), t.userId,
        ts(t.audit.createdAt), t.audit.createdBy, tsOpt(t.audit.updatedAt), t.audit.updatedBy,
        t.audit.isDeleted, tsOpt(t.audit.deletedAt), t.audit.deletedBy
      )

    def toDomain(r: TaskRow): TaskItem =
      Priority
        .fromInt(r.priorityValue)
        .toRight(DomainError.InvalidPriorityValue(r.priorityValue))
        .flatMap(p => Urgency.from(p, r.dueDate.map(_.toLocalDate))) // High + NULL => Left
        .map { urgency =>
          TaskItem(
            r.id, r.title, r.description, urgency, r.completedAt.map(_.toInstant), r.isCompleted, r.userId,
            toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
          )
        }
        .fold(e => sys.error(s"DB invariant ihlali (task id=${r.id}): ${e.message}"), identity)
  }

  implicit val taskItemCategoryMapper: RowMapper[TaskItemCategory, TaskItemCategoryRow] =
    new RowMapper[TaskItemCategory, TaskItemCategoryRow] {

      def toRow(l: TaskItemCategory): TaskItemCategoryRow =
        TaskItemCategoryRow(
          l.id, l.taskItemId, l.categoryId,
          ts(l.audit.createdAt), l.audit.createdBy, tsOpt(l.audit.updatedAt), l.audit.updatedBy,
          l.audit.isDeleted, tsOpt(l.audit.deletedAt), l.audit.deletedBy
        )

      def toDomain(r: TaskItemCategoryRow): TaskItemCategory =
        TaskItemCategory(
          r.id, r.taskItemId, r.categoryId,
          toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
        )
    }
}
