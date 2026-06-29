package drp.asset.domain

import java.time.Instant

import drp.shared.domain.DomainError

/** Strongly-typed entity id (0 = unsaved; the DB assigns the real `BIGSERIAL`). */
final case class EntityId(value: Long) extends AnyVal

/**
 * A protected entity (brand/org) — the anchor every other DRP record references. Pure domain:
 * constructed only via `Entity.create` (smart constructor). Timestamps are DB-managed (defaults +
 * `set_updated_at` trigger); the values carried here are for read/render.
 */
final case class Entity(
    id: EntityId,
    name: String,
    entityType: EntityType,
    createdAt: Instant,
    updatedAt: Instant
) {

  /** Apply an edit: validate the new name, swap the type, bump `updatedAt` (DB trigger is authoritative). */
  def edit(newName: String, newType: String, now: Instant): Either[DomainError, Entity] =
    Entity.requireNonBlank(newName).map(n => copy(name = n, entityType = EntityType.fromCode(newType), updatedAt = now))
}

object Entity {

  /** Smart constructor: blank name → `EmptyEntityName`; type is open (never fails). `id=0` until persisted. */
  def create(name: String, entityType: String, now: Instant): Either[DomainError, Entity] =
    requireNonBlank(name).map(n => Entity(EntityId(0L), n, EntityType.fromCode(entityType), now, now))

  private def requireNonBlank(s: String): Either[DomainError, String] =
    if (s == null || s.trim.isEmpty) Left(DomainError.EmptyEntityName) else Right(s.trim)
}
