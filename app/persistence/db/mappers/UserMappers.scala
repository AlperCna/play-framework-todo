package persistence.db.mappers

import domain.user.User
import persistence.db.AuditMapper._
import persistence.db.{RowMapper, UserRow}

/** `domain.user` entity'lerinin Row <-> domain cevrimi. */
object UserMappers {

  implicit val userMapper: RowMapper[User, UserRow] = new RowMapper[User, UserRow] {

    def toRow(u: User): UserRow =
      UserRow(
        u.id, u.email, u.password,
        ts(u.audit.createdAt), u.audit.createdBy, tsOpt(u.audit.updatedAt), u.audit.updatedBy,
        u.audit.isDeleted, tsOpt(u.audit.deletedAt), u.audit.deletedBy
      )

    def toDomain(r: UserRow): User =
      User(
        r.id, r.email, r.password,
        toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
      )
  }
}
