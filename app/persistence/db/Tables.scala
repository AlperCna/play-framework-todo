package persistence.db

import java.sql.{Date => SqlDate, Timestamp}

import slick.jdbc.JdbcProfile

/**
 * Slick tablo tanimlari ve DUZ (flat) row modelleri.
 *
 * DOMAIN MODELI != DB MODELI: Buradaki `*Row` tipleri ilisksel satira birebir
 * oturur. Iki bicimde domain'den ayrisirlar:
 *   1) `urgency` ADT'si YOKTUR -> `priorityValue` (Int) + `dueDate` (Date).
 *   2) Zaman tipleri JDBC-yerel `java.sql.Timestamp`/`java.sql.Date`'tir
 *      (domain'de `Instant`/`LocalDate`). Boylece Slick'in yerlesik, string'e
 *      cevirmeyen kolon destegini kullaniriz; ozel implicit column type
 *      gerekmez (ozel tipler tuple Shape turetmesini bozuyordu).
 * Domain'e/tan donusum [[Mappers]]'da yapilir.
 */
trait Tables {

  protected val profile: JdbcProfile
  import profile.api._

  // ----------------------------- USERS -----------------------------
  class Users(tag: Tag) extends Table[UserRow](tag, "users") {
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

  // --------------------------- CATEGORIES ---------------------------
  class Categories(tag: Tag) extends Table[CategoryRow](tag, "categories") {
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

  // ------------------------------ TASKS -----------------------------
  class Tasks(tag: Tag) extends Table[TaskRow](tag, "tasks") {
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

  // ----------------------- TASK_ITEM_CATEGORIES ---------------------
  class TaskItemCategories(tag: Tag) extends Table[TaskItemCategoryRow](tag, "task_item_categories") {
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

// =====================================================================
// DUZ row modelleri (profil'den bagimsiz; trait disinda top-level).
// Zaman tipleri JDBC-yerel: Timestamp (DATETIME2) ve SqlDate (DATE).
// =====================================================================

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
