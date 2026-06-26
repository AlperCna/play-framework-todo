package todo.shared.infrastructure

import java.sql.Timestamp
import java.time.Instant

import todo.shared.domain.AuditInfo

object AuditMapper {

  def ts(i: Instant): Timestamp = Timestamp.from(i)

  def tsOpt(i: Option[Instant]): Option[Timestamp] = i.map(Timestamp.from)

  def toAudit(
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
