package drp.asset.domain

import java.time.Instant

/**
 * A protected entity (brand / institution / person) — the owner anchor every downstream
 * DRP record references. `entityType` is a free-text classification (no fixed enumeration).
 */
final case class Entity(
    id: Long,
    name: String,
    entityType: String,
    createdAt: Instant,
    updatedAt: Instant
)

object Entity {

  /**
   * Smart constructor for a NEW entity. `id` and timestamps are placeholders here —
   * the database assigns them on insert. Only `name` is domain-validated (FR-002);
   * `entityType` is accepted as-is once trimmed (FR-003), the web form enforces non-empty.
   */
  def create(name: String, entityType: String): Either[AssetDomainError, Entity] = {
    val trimmedName = Option(name).map(_.trim).getOrElse("")
    if (trimmedName.isEmpty) Left(AssetDomainError.BlankEntityName)
    else Right(Entity(0L, trimmedName, Option(entityType).map(_.trim).getOrElse(""), Instant.EPOCH, Instant.EPOCH))
  }
}
