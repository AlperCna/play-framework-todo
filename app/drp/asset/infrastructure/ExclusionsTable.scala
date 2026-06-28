package drp.asset.infrastructure

import java.time.Instant

import drp.shared.infrastructure.MonaPgProfile

/**
 * Slick mapping for the `exclusions` table (created by V001). `entity_id` is nullable in the schema
 * (reserved for future global exclusions), so the row types it as `Option[Long]`; this slice always
 * writes `Some(entityId)`. `is_active` / `created_by` / `created_at` / `updated_at` are DB-managed
 * (defaults + trigger) and never written by the application.
 */
trait ExclusionsTable {

  protected val profile: MonaPgProfile
  import profile.api._

  class Exclusions(tag: Tag) extends Table[ExclusionRow](tag, "exclusions") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def entityId  = column[Option[Long]]("entity_id")
    def value     = column[String]("value")
    def matchType = column[String]("match_type")
    def reason    = column[String]("reason")
    def isActive  = column[Boolean]("is_active")
    def createdBy = column[String]("created_by")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")

    def * = (id, entityId, value, matchType, reason, isActive, createdBy, createdAt, updatedAt).mapTo[ExclusionRow]
  }

  lazy val exclusions = TableQuery[Exclusions]
}

final case class ExclusionRow(
    id: Long,
    entityId: Option[Long],
    value: String,
    matchType: String,
    reason: String,
    isActive: Boolean,
    createdBy: String,
    createdAt: Instant,
    updatedAt: Instant
)
