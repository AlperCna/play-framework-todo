package drp.asset.domain

import java.time.Instant

import drp.shared.domain._

final case class AssetGroup(
    id: AssetGroupId,
    entityId: EntityId,
    name: String,
    createdAt: Instant,
    updatedAt: Instant
)

object AssetGroup {
  def create(
      id: AssetGroupId,
      entityId: EntityId,
      name: String,
      createdAt: Instant,
      updatedAt: Instant
  ): Either[DomainError, AssetGroup] =
    CommonValues.nonEmpty("name", name).map(AssetGroup(id, entityId, _, createdAt, updatedAt))
}
