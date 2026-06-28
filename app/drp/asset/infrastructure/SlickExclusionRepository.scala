package drp.asset.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase

import drp.asset.application.ports.ExclusionRepository
import drp.asset.domain.{Exclusion, ExclusionMatchType}
import drp.shared.infrastructure.MonaPgProfile

/** PostgreSQL adapter for `ExclusionRepository`, bound to the dedicated `drp` Slick database. */
@Singleton
class SlickExclusionRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends ExclusionRepository
    with HasDatabaseConfigProvider[MonaPgProfile]
    with ExclusionsTable {

  import profile.api._

  override def create(exclusion: Exclusion): Future[Exclusion] = {
    // Insert only entity_id + value + match_type + reason; id/is_active/created_by/timestamps are DB-assigned.
    val insert =
      (exclusions.map(e => (e.entityId, e.value, e.matchType, e.reason)) returning exclusions.map(_.id)) +=
        ((Some(exclusion.entityId), exclusion.value, exclusion.matchType.asValue, exclusion.reason))
    db.run(insert).map(newId => exclusion.copy(id = newId))
  }

  override def listByEntity(entityId: Long): Future[Seq[Exclusion]] =
    db.run(exclusions.filter(_.entityId === entityId).sortBy(_.id).result).map(_.map(toDomain))

  override def existsActiveDuplicate(
      entityId: Long,
      value: String,
      matchType: ExclusionMatchType
  ): Future[Boolean] =
    db.run(
      exclusions
        .filter(e => e.entityId === entityId && e.value === value && e.matchType === matchType.asValue && e.isActive)
        .exists
        .result
    )

  private def toDomain(r: ExclusionRow): Exclusion =
    Exclusion(
      r.id,
      r.entityId.getOrElse(0L),
      r.value,
      ExclusionMatchType.fromValue(r.matchType).getOrElse(ExclusionMatchType.Exact),
      r.reason,
      r.isActive,
      r.createdBy,
      r.createdAt,
      r.updatedAt
    )
}
