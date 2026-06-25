package todo.shared.domain

trait AuditableEntity {
  def id: Long
  def audit: AuditInfo
  def isDeleted: Boolean = audit.isDeleted
}
