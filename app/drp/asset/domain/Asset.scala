package drp.asset.domain

import java.time.Instant

import drp.shared.domain.DomainError

/** Strongly-typed asset id (0 = unsaved; DB assigns the real `BIGSERIAL`). */
final case class AssetId(value: Long) extends AnyVal

/**
 * A digital asset to protect, owned by exactly one entity. Pure domain; built via `Asset.create`.
 * May reference an optional asset group (same-entity validity is checked in the service — FR-005).
 * `isActive` defaults true (no deactivation here). Timestamps are DB-managed.
 */
final case class Asset(
    id: AssetId,
    entityId: EntityId,
    assetGroupId: Option[AssetGroupId],
    assetType: AssetType,
    value: String,
    metadata: AssetMetadata,
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
) {

  def edit(
      newType: String,
      newValue: String,
      newMetadata: AssetMetadata,
      now: Instant,
      newGroupId: Option[AssetGroupId] = None
  ): Either[DomainError, Asset] =
    for {
      v <- Asset.requireNonBlank(newValue)
      t <- AssetType.fromCode(newType).toRight(DomainError.InvalidAssetType(newType))
    } yield copy(assetGroupId = newGroupId, assetType = t, value = v, metadata = newMetadata, updatedAt = now)
}

object Asset {

  /** Smart ctor: blank value → `EmptyAssetValue`; unknown type → `InvalidAssetType`. New asset is active. */
  def create(
      entityId: EntityId,
      assetType: String,
      value: String,
      metadata: AssetMetadata,
      now: Instant,
      assetGroupId: Option[AssetGroupId] = None
  ): Either[DomainError, Asset] =
    for {
      v <- requireNonBlank(value)
      t <- AssetType.fromCode(assetType).toRight(DomainError.InvalidAssetType(assetType))
    } yield Asset(AssetId(0L), entityId, assetGroupId, t, v, metadata, isActive = true, now, now)

  private def requireNonBlank(s: String): Either[DomainError, String] =
    if (s == null || s.trim.isEmpty) Left(DomainError.EmptyAssetValue) else Right(s.trim)
}
