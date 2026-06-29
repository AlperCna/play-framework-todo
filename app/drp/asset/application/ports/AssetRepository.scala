package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.{Asset, AssetId, AssetType, EntityId}

/** Persistence port for assets (the `asset` module is the single writer of `assets`). */
trait AssetRepository {
  def add(a: Asset): Future[Asset]
  def get(id: AssetId): Future[Option[Asset]]
  /** True if an ACTIVE asset already has this (entity, asset_type, value) — the duplicate guard (FR-004). */
  def existsActive(entityId: EntityId, assetType: AssetType, value: String): Future[Boolean]
  def update(a: Asset): Future[Option[Asset]]
  /** All assets for one entity (bounded per parent — loaded in full, Constitution IV exemption). */
  def listByEntity(entityId: EntityId): Future[Seq[Asset]]
}
