package todo.task.infrastructure

import java.sql.Timestamp

import todo.shared.infrastructure.BaseTables

trait TaskItemCategoriesTable extends BaseTables {

  import profile.api._

  class TaskItemCategories(tag: Tag) extends BaseTable[TaskItemCategoryRow](tag, "task_item_categories") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def taskItemId = column[Long]("task_item_id")
    def categoryId = column[Long]("category_id")
    def createdAt  = column[Timestamp]("created_at")
    def createdBy  = column[String]("created_by")
    def updatedAt  = column[Option[Timestamp]]("updated_at")
    def updatedBy  = column[String]("updated_by")
    def isDeleted  = column[Boolean]("is_deleted")
    def deletedAt  = column[Option[Timestamp]]("deleted_at")
    def deletedBy  = column[String]("deleted_by")

    def * =
      (id, taskItemId, categoryId, createdAt, createdBy, updatedAt, updatedBy, isDeleted, deletedAt, deletedBy)
        .mapTo[TaskItemCategoryRow]
  }
  lazy val taskItemCategories = TableQuery[TaskItemCategories]
}

final case class TaskItemCategoryRow(
    id: Long,
    taskItemId: Long,
    categoryId: Long,
    createdAt: Timestamp,
    createdBy: String,
    updatedAt: Option[Timestamp],
    updatedBy: String,
    isDeleted: Boolean,
    deletedAt: Option[Timestamp],
    deletedBy: String
)
