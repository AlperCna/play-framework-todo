package todo.shared.domain

import java.time.Instant

case class AuditInfo(
    createdAt: Instant,
    createdBy: String,
    updatedAt: Option[Instant] = None,
    updatedBy: String = "",
    isDeleted: Boolean = false,
    deletedAt: Option[Instant] = None,
    deletedBy: String = ""
) {

  def updated(now: Instant, by: String): AuditInfo =
    copy(updatedAt = Some(now), updatedBy = by)

  def deleted(now: Instant, by: String): AuditInfo =
    copy(isDeleted = true, deletedAt = Some(now), deletedBy = by)

  def softDelete(): AuditInfo =
    copy(isDeleted = true)

  def restore(): AuditInfo =
    copy(isDeleted = false, deletedAt = None, deletedBy = "")
}

object AuditInfo {

  def create(now: Instant, by: String): AuditInfo =
    AuditInfo(createdAt = now, createdBy = by)
}
