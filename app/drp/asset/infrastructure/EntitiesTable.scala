package drp.asset.infrastructure

import java.time.Instant

import drp.shared.infrastructure.MonaPgProfile

/**
 * Slick mapping for the `entities` table (created by V001). Mixed into the asset module's
 * Slick repository, which supplies the `drp` PostgreSQL profile. `created_at` / `updated_at`
 * are DB-managed (default + trigger) and never written by the application.
 */
trait EntitiesTable {

  protected val profile: MonaPgProfile
  import profile.api._

  class Entities(tag: Tag) extends Table[EntityRow](tag, "entities") {
    def id         = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name       = column[String]("name")
    def entityType = column[String]("type")
    def createdAt  = column[Instant]("created_at")
    def updatedAt  = column[Instant]("updated_at")

    def * = (id, name, entityType, createdAt, updatedAt).mapTo[EntityRow]
  }

  lazy val entities = TableQuery[Entities]
}

final case class EntityRow(
    id: Long,
    name: String,
    entityType: String,
    createdAt: Instant,
    updatedAt: Instant
)
