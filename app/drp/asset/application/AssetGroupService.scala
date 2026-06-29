package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{AssetGroup, AssetGroupId, EntityId}
import drp.shared.domain.DomainError

/** Application service for asset groups — parent-entity check + unique (entity, name). */
trait AssetGroupService {
  def create(entityId: EntityId, name: String): Future[Either[DomainError, AssetGroup]]
  def update(id: AssetGroupId, name: String): Future[Either[DomainError, AssetGroup]]
  def get(id: AssetGroupId): Future[Option[AssetGroup]]
  def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]]
}
