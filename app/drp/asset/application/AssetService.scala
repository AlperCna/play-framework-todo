package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{Asset, AssetId, AssetMetadata, EntityId}
import drp.shared.domain.DomainError

/** Application service for assets — validates, checks the parent entity, and guards duplicates. */
trait AssetService {
  def create(entityId: EntityId, assetType: String, value: String, metadata: AssetMetadata): Future[Either[DomainError, Asset]]
  def update(id: AssetId, assetType: String, value: String, metadata: AssetMetadata): Future[Either[DomainError, Asset]]
  def get(id: AssetId): Future[Option[Asset]]
  def listByEntity(entityId: EntityId): Future[Seq[Asset]]
}
