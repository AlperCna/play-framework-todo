package todo.user.infrastructure

import todo.shared.infrastructure.AuditMapper
import todo.shared.infrastructure.RowMapper
import todo.user.domain.User

object UserMappers {

  implicit val userMapper: RowMapper[User, UserRow] = new RowMapper[User, UserRow] {

    def toRow(u: User): UserRow =
      UserRow(
        u.id, u.email, u.password,
        AuditMapper.ts(u.audit.createdAt), u.audit.createdBy,
        AuditMapper.tsOpt(u.audit.updatedAt), u.audit.updatedBy,
        u.audit.isDeleted, AuditMapper.tsOpt(u.audit.deletedAt), u.audit.deletedBy
      )

    def toDomain(r: UserRow): User =
      User(
        r.id, r.email, r.password,
        AuditMapper.toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
      )
  }
}
