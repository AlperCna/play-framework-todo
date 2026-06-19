package persistence.db.mappers

import domain.category.Category
import persistence.db.AuditMapper._
import persistence.db.{CategoryRow, RowMapper}

/** `domain.category` entity'lerinin Row <-> domain cevrimi. */
object CategoryMappers {

  implicit val categoryMapper: RowMapper[Category, CategoryRow] =
    new RowMapper[Category, CategoryRow] {

      def toRow(c: Category): CategoryRow =
        CategoryRow(
          c.id, c.name, c.description, c.userId,
          ts(c.audit.createdAt), c.audit.createdBy, tsOpt(c.audit.updatedAt), c.audit.updatedBy,
          c.audit.isDeleted, tsOpt(c.audit.deletedAt), c.audit.deletedBy
        )

      def toDomain(r: CategoryRow): Category =
        Category(
          r.id, r.name, r.description, r.userId,
          toAudit(r.createdAt, r.createdBy, r.updatedAt, r.updatedBy, r.isDeleted, r.deletedAt, r.deletedBy)
        )
    }
}
