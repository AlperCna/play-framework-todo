package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.EntityId
import drp.shared.application.{Page, PageRequest}

/** Read-models returned across the module boundary (typed projections — no domain/Slick types leak). */
final case class ExclusionView(value: String, matchType: String, reason: String)
final case class EntityView(id: Long, name: String, entityType: String)
final case class AssetView(id: Long, entityId: Long, assetType: String, value: String, isActive: Boolean)
final case class EntityWithAssets(entity: EntityView, assets: Seq[AssetView])

/**
 * Read seam for the discovery module (and future consumers).
 * `activeExclusions` returns the allowlist for an entity (no matching applied);
 * `resolveEntityWithAssets` resolves an entity together with its assets for downstream anchoring;
 * `listEntities` provides the paginated entity roster for UI selectors;
 * `resolveAsset` looks up a single asset by raw id.
 */
trait AssetReadPort {
  def activeExclusions(entityId: EntityId): Future[Seq[ExclusionView]]
  def resolveEntityWithAssets(entityId: EntityId): Future[Option[EntityWithAssets]]
  def listEntities(page: PageRequest): Future[Page[EntityView]]
  def resolveAsset(assetId: Long): Future[Option[AssetView]]
}
