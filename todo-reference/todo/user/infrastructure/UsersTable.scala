package todo.user.infrastructure

import java.sql.Timestamp

import todo.shared.infrastructure.BaseTables

trait UsersTable extends BaseTables {

  import profile.api._

  class Users(tag: Tag) extends BaseTable[UserRow](tag, "users") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email     = column[String]("email")
    def password  = column[String]("password")
    def createdAt = column[Timestamp]("created_at")
    def createdBy = column[String]("created_by")
    def updatedAt = column[Option[Timestamp]]("updated_at")
    def updatedBy = column[String]("updated_by")
    def isDeleted = column[Boolean]("is_deleted")
    def deletedAt = column[Option[Timestamp]]("deleted_at")
    def deletedBy = column[String]("deleted_by")

    def * =
      (id, email, password, createdAt, createdBy, updatedAt, updatedBy, isDeleted, deletedAt, deletedBy)
        .mapTo[UserRow]
  }
  lazy val users = TableQuery[Users]
}

final case class UserRow(
    id: Long,
    email: String,
    password: String,
    createdAt: Timestamp,
    createdBy: String,
    updatedAt: Option[Timestamp],
    updatedBy: String,
    isDeleted: Boolean,
    deletedAt: Option[Timestamp],
    deletedBy: String
)
