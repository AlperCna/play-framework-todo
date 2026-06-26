package drp.asset.domain

import java.time.Instant

import drp.shared.domain._

final case class EntityType(value: String) extends AnyVal
object EntityType {
  def create(value: String): Either[DomainError, EntityType] =
    CommonValues.nonEmpty("entityType", value).map(EntityType(_))
}

final case class Entity(
    id: EntityId,
    name: String,
    entityType: EntityType,
    createdAt: Instant,
    updatedAt: Instant
)

object Entity {
  def create(
      id: EntityId,
      name: String,
      entityType: EntityType,
      createdAt: Instant,
      updatedAt: Instant
  ): Either[DomainError, Entity] =
    CommonValues.nonEmpty("name", name).map(Entity(id, _, entityType, createdAt, updatedAt))
}
