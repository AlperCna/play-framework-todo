package drp.asset.domain

import java.time.Instant

import drp.shared.domain.DomainError

/**
 * An optional grouping of assets within a single entity (`AssetGroupId` is declared in its own file —
 * `Asset` references it). Pure domain; built via `AssetGroup.create`.
 */
final case class AssetGroup(
    id: AssetGroupId,
    entityId: EntityId,
    name: String,
    createdAt: Instant,
    updatedAt: Instant
) {
  def rename(newName: String, now: Instant): Either[DomainError, AssetGroup] =
    AssetGroup.requireNonBlank(newName).map(n => copy(name = n, updatedAt = now))
}

object AssetGroup {

  /** Smart ctor: blank name → `EmptyAssetGroupName`. Scoped to its entity. */
  def create(entityId: EntityId, name: String, now: Instant): Either[DomainError, AssetGroup] =
    requireNonBlank(name).map(n => AssetGroup(AssetGroupId(0L), entityId, n, now, now))

  private def requireNonBlank(s: String): Either[DomainError, String] =
    if (s == null || s.trim.isEmpty) Left(DomainError.EmptyAssetGroupName) else Right(s.trim)
}
