package todo.task.infrastructure

import java.sql.{Date => SqlDate, Timestamp}

import todo.shared.infrastructure.BaseTables

trait TasksTable extends BaseTables {

  import profile.api._

  class Tasks(tag: Tag) extends BaseTable[TaskRow](tag, "tasks") {
    def id            = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def title         = column[String]("title")
    def description   = column[Option[String]]("description")
    def priorityValue = column[Int]("priority_value")
    def dueDate       = column[Option[SqlDate]]("due_date")
    def isCompleted   = column[Boolean]("is_completed")
    def completedAt   = column[Option[Timestamp]]("completed_at")
    def userId        = column[Option[Long]]("user_id")
    def createdAt     = column[Timestamp]("created_at")
    def createdBy     = column[String]("created_by")
    def updatedAt     = column[Option[Timestamp]]("updated_at")
    def updatedBy     = column[String]("updated_by")
    def isDeleted     = column[Boolean]("is_deleted")
    def deletedAt     = column[Option[Timestamp]]("deleted_at")
    def deletedBy     = column[String]("deleted_by")

    def * =
      (
        id, title, description, priorityValue, dueDate, isCompleted, completedAt, userId,
        createdAt, createdBy, updatedAt, updatedBy, isDeleted, deletedAt, deletedBy
      ).mapTo[TaskRow]
  }
  lazy val tasks = TableQuery[Tasks]
}

final case class TaskRow(
    id: Long,
    title: String,
    description: Option[String],
    priorityValue: Int,
    dueDate: Option[SqlDate],
    isCompleted: Boolean,
    completedAt: Option[Timestamp],
    userId: Option[Long],
    createdAt: Timestamp,
    createdBy: String,
    updatedAt: Option[Timestamp],
    updatedBy: String,
    isDeleted: Boolean,
    deletedAt: Option[Timestamp],
    deletedBy: String
)
