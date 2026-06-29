package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{Asset, AssetGroupId, AssetId, AssetMetadata, EntityId}
import drp.shared.domain.DomainError

/** Application service for assets — validates, checks the parent entity, the (optional) group, and duplicates. */
trait AssetService {
  def create(
      entityId: EntityId,
      assetType: String,
      value: String,
      metadata: AssetMetadata,
      assetGroupId: Option[AssetGroupId] = None
  ): Future[Either[DomainError, Asset]]

  def update(
      id: AssetId,
      assetType: String,
      value: String,
      metadata: AssetMetadata,
      assetGroupId: Option[AssetGroupId] = None
  ): Future[Either[DomainError, Asset]]

  def get(id: AssetId): Future[Option[Asset]]
  def listByEntity(entityId: EntityId): Future[Seq[Asset]]
}
