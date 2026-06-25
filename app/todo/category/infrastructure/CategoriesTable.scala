package todo.category.infrastructure

import java.sql.Timestamp

import todo.shared.infrastructure.BaseTables

trait CategoriesTable extends BaseTables {

  import profile.api._

  class Categories(tag: Tag) extends BaseTable[CategoryRow](tag, "categories") {
    def id          = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name        = column[String]("name")
    def description = column[String]("description")
    def userId      = column[Long]("user_id")
    def createdAt   = column[Timestamp]("created_at")
    def createdBy   = column[String]("created_by")
    def updatedAt   = column[Option[Timestamp]]("updated_at")
    def updatedBy   = column[String]("updated_by")
    def isDeleted   = column[Boolean]("is_deleted")
    def deletedAt   = column[Option[Timestamp]]("deleted_at")
    def deletedBy   = column[String]("deleted_by")

    def * =
      (id, name, description, userId, createdAt, createdBy, updatedAt, updatedBy, isDeleted, deletedAt, deletedBy)
        .mapTo[CategoryRow]
  }
  lazy val categories = TableQuery[Categories]
}

final case class CategoryRow(
    id: Long,
    name: String,
    description: String,
    userId: Long,
    createdAt: Timestamp,
    createdBy: String,
    updatedAt: Option[Timestamp],
    updatedBy: String,
    isDeleted: Boolean,
    deletedAt: Option[Timestamp],
    deletedBy: String
)
