package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.EntityId

/** Read-models returned across the module boundary (typed projections — no domain/Slick types leak). */
final case class ExclusionView(value: String, matchType: String, reason: String)
final case class EntityView(id: Long, name: String, entityType: String)
final case class AssetView(id: Long, assetType: String, value: String)
final case class EntityWithAssets(entity: EntityView, assets: Seq[AssetView])

/**
 * The read seam the FUTURE discovery module consults. DEFINED here; consumption is out of scope.
 * `activeExclusions` returns the allowlist for an entity (no matching applied);
 * `resolveEntityWithAssets` resolves an entity together with its assets for downstream anchoring.
 */
trait AssetReadPort {
  def activeExclusions(entityId: EntityId): Future[Seq[ExclusionView]]
  def resolveEntityWithAssets(entityId: EntityId): Future[Option[EntityWithAssets]]
}
