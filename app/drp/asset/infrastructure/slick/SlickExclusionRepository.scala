package drp.asset.infrastructure.slick

import java.time.Instant

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase

import drp.asset.application.ports.ExclusionRepository
import drp.asset.domain.{EntityId, Exclusion, ExclusionId, ExclusionReason, MatchType}
import drp.shared.infrastructure.MonaPgProfile

private[slick] final case class ExclusionRow(
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

/** Slick adapter for `ExclusionRepository` on the `drp` datasource. Entity-scoped only in this feature. */
@Singleton
class SlickExclusionRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends ExclusionRepository {

  private val dbConfig = dbConfigProvider.get[MonaPgProfile]
  import dbConfig.profile.api._
  private val db = dbConfig.db

  private class ExclusionsTable(tag: Tag) extends Table[ExclusionRow](tag, "exclusions") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def entityId = column[Option[Long]]("entity_id")
    def value = column[String]("value")
    def matchType = column[String]("match_type")
    def reason = column[String]("reason")
    def isActive = column[Boolean]("is_active")
    def createdBy = column[String]("created_by")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    def * = (id, entityId, value, matchType, reason, isActive, createdBy, createdAt, updatedAt).mapTo[ExclusionRow]
  }

  private val exclusions = TableQuery[ExclusionsTable]

  // entity_id is nullable in the schema (global exclusions are a future case); this feature only
  // creates/reads entity-scoped rows, so a present entity_id is expected here.
  private def toDomain(r: ExclusionRow): Exclusion =
    Exclusion(
      ExclusionId(r.id),
      EntityId(r.entityId.getOrElse(0L)),
      r.value,
      MatchType.fromCode(r.matchType).getOrElse(MatchType.Exact),
      ExclusionReason.fromCode(r.reason),
      r.isActive,
      r.createdBy,
      r.createdAt,
      r.updatedAt
    )

  override def add(x: Exclusion): Future[Exclusion] = {
    val insert = (exclusions.map(t => (t.entityId, t.value, t.matchType, t.reason)) returning exclusions.map(_.id)) +=
      ((Some(x.entityId.value), x.value, MatchType.toCode(x.matchType), ExclusionReason.toCode(x.reason)))
    db.run(insert)
      .flatMap(newId => db.run(exclusions.filter(_.id === newId).result.head))
      .map(toDomain)
  }

  override def get(id: ExclusionId): Future[Option[Exclusion]] =
    db.run(exclusions.filter(_.id === id.value).result.headOption).map(_.map(toDomain))

  override def existsActive(entityId: EntityId, value: String, matchType: MatchType): Future[Boolean] =
    db.run(
      exclusions
        .filter(x => x.entityId === entityId.value && x.value === value.trim && x.matchType === MatchType.toCode(matchType) && x.isActive)
        .exists
        .result
    )

  override def update(x: Exclusion): Future[Option[Exclusion]] = {
    val q = exclusions.filter(_.id === x.id.value)
      .map(t => (t.value, t.matchType, t.reason))
      .update((x.value, MatchType.toCode(x.matchType), ExclusionReason.toCode(x.reason)))
    db.run(q).flatMap {
      case 0 => Future.successful(None)
      case _ => db.run(exclusions.filter(_.id === x.id.value).result.headOption).map(_.map(toDomain))
    }
  }

  override def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]] =
    db.run(exclusions.filter(x => x.entityId === entityId.value && x.isActive).sortBy(_.id).result).map(_.map(toDomain))
}
