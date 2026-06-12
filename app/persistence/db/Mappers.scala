package persistence.db

import java.sql.{Date => SqlDate, Timestamp}
import java.time.Instant

import domain.category.Category
import domain.common.{AuditInfo, DomainError, Priority}
import domain.task.{TaskItem, TaskItemCategory, Urgency}
import domain.user.User

/**
 * DUZ DB satiri (`*Row`) <-> zengin domain entity'si cevrim katmani.
 *
 * BURASI "domain modeli != DB modeli" sinirinin yasandigi yer; iki tur cevrim var:
 *   - Yapi: `Urgency` ADT'si `priorityValue` + `dueDate`'e DUZLESIR; `AuditInfo`
 *     7 ayri alana acilir.
 *   - Tip: domain'in `Instant`/`LocalDate` tipleri JDBC-yerel
 *     `java.sql.Timestamp`/`java.sql.Date`'e cevrilir (Slick'in string-parse
 *     etmeyen yerlesik destegi icin).
 *
 * Okuma sirasinda TaskItem kurulumu `Either` uretir (High + NULL illegal); DB
 * CHECK constraint'i bunu zaten engelledigi icin burada "olmamasi gereken" durum
 * olarak `sys.error` ile patlatiriz (data-butunlugu hatasi).
 */
object Mappers {

  // ------------------------------ User ------------------------------
  def toRow(u: User): UserRow =
    UserRow(
      u.id, u.email, u.password,
      ts(u.audit.createdAt), u.audit.createdBy, tsOpt(u.audit.updatedAt), u.audit.updatedBy,
      u.audit.isDeleted, tsOpt(u.audit.deletedAt), u.audit.deletedBy
    )

  def toUser(r: UserRow): User =
    User(
      r.id, r.email, r.password,
      auditOf(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
    )

  // ---------------------------- Category ----------------------------
  def toRow(c: Category): CategoryRow =
    CategoryRow(
      c.id, c.name, c.description, c.userId,
      ts(c.audit.createdAt), c.audit.createdBy, tsOpt(c.audit.updatedAt), c.audit.updatedBy,
      c.audit.isDeleted, tsOpt(c.audit.deletedAt), c.audit.deletedBy
    )

  def toCategory(r: CategoryRow): Category =
    Category(
      r.id, r.name, r.description, r.userId,
      auditOf(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
    )

  // ------------------------ TaskItemCategory ------------------------
  def toRow(l: TaskItemCategory): TaskItemCategoryRow =
    TaskItemCategoryRow(
      l.id, l.taskItemId, l.categoryId,
      ts(l.audit.createdAt), l.audit.createdBy, tsOpt(l.audit.updatedAt), l.audit.updatedBy,
      l.audit.isDeleted, tsOpt(l.audit.deletedAt), l.audit.deletedBy
    )

  def toLink(r: TaskItemCategoryRow): TaskItemCategory =
    TaskItemCategory(
      r.id, r.taskItemId, r.categoryId,
      auditOf(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
    )

  // ------------------------- TaskItem (ADT) -------------------------
  def toRow(t: TaskItem): TaskRow =
    TaskRow(
      t.id, t.title, t.description,
      t.priority.value, t.dueDate.map(d => SqlDate.valueOf(d)), // <-- urgency DUZLESIR
      t.isCompleted, tsOpt(t.completedAt), t.userId,
      ts(t.audit.createdAt), t.audit.createdBy, tsOpt(t.audit.updatedAt), t.audit.updatedBy,
      t.audit.isDeleted, tsOpt(t.audit.deletedAt), t.audit.deletedBy
    )

  def toTask(r: TaskRow): TaskItem =
    Priority
      .fromInt(r.priorityValue)
      .toRight(DomainError.InvalidPriorityValue(r.priorityValue))
      .flatMap(p => Urgency.from(p, r.dueDate.map(_.toLocalDate))) // High + NULL => Left
      .map { urgency =>
        TaskItem(
          r.id, r.title, r.description, urgency, r.completedAt.map(_.toInstant), r.isCompleted, r.userId,
          auditOf(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
        )
      }
      .fold(e => sys.error(s"DB invariant ihlali (task id=${r.id}): ${e.message}"), identity)

  // ----------------------- zaman / audit cevrimi --------------------
  private def ts(i: Instant): Timestamp = Timestamp.from(i)
  private def tsOpt(i: Option[Instant]): Option[Timestamp] = i.map(Timestamp.from)

  private def auditOf(
      createdAt: Timestamp,
      createdBy: String,
      updatedAt: Option[Timestamp],
      updatedBy: String,
      isDeleted: Boolean,
      deletedAt: Option[Timestamp],
      deletedBy: String
  ): AuditInfo =
    AuditInfo(
      createdAt.toInstant, createdBy, updatedAt.map(_.toInstant), updatedBy,
      isDeleted, deletedAt.map(_.toInstant), deletedBy
    )
}
