package todo.category.infrastructure

import todo.category.domain.Category
import todo.shared.infrastructure.AuditMapper
import todo.shared.infrastructure.RowMapper

object CategoryMappers {

  implicit val categoryMapper: RowMapper[Category, CategoryRow] =
    new RowMapper[Category, CategoryRow] {

      def toRow(c: Category): CategoryRow =
        CategoryRow(
          c.id, c.name, c.description, c.userId,
          AuditMapper.ts(c.audit.createdAt), c.audit.createdBy,
          AuditMapper.tsOpt(c.audit.updatedAt), c.audit.updatedBy,
          c.audit.isDeleted, AuditMapper.tsOpt(c.audit.deletedAt), c.audit.deletedBy
        )

      def toDomain(r: CategoryRow): Category =
        Category(
          r.id, r.name, r.description, r.userId,
          AuditMapper.toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
        )
    }
}
