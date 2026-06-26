package drp.asset.domain

import java.time.Instant

import drp.shared.domain._

sealed trait AssetType { def value: String }
object AssetType {
  case object Domain    extends AssetType { val value = "domain" }
  case object Subdomain extends AssetType { val value = "subdomain" }
}

final case class Asset(
    id: AssetId,
    entityId: EntityId,
    assetGroupId: Option[AssetGroupId],
    assetType: AssetType,
    value: String,
    metadata: Metadata,
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
) {
  def deactivate(updatedAt: Instant): Asset =
    copy(isActive = false, updatedAt = updatedAt)
}

object Asset {
  def create(
      id: AssetId,
      entityId: EntityId,
      assetGroupId: Option[AssetGroupId],
      assetType: AssetType,
      value: String,
      metadata: Metadata,
      isActive: Boolean,
      createdAt: Instant,
      updatedAt: Instant
  ): Either[DomainError, Asset] =
    CommonValues.nonEmpty("value", value)
      .map(Asset(id, entityId, assetGroupId, assetType, _, metadata, isActive, createdAt, updatedAt))
}
