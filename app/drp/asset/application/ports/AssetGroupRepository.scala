package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.{AssetGroup, AssetGroupId, EntityId}

/** Persistence port for asset groups (the `asset` module is the single writer of `asset_groups`). */
trait AssetGroupRepository {
  def add(g: AssetGroup): Future[AssetGroup]
  def get(id: AssetGroupId): Future[Option[AssetGroup]]
  def existsByEntityAndName(entityId: EntityId, name: String): Future[Boolean]
  def update(g: AssetGroup): Future[Option[AssetGroup]]
  def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]]
}
