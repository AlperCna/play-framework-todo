package drp.asset.web

import drp.asset.domain.Entity

/** Read model for Twirl — flat primitives only (no domain/persistence type leaks into views). */
final case class EntityViewModel(
    id: Long,
    name: String,
    entityType: String,
    createdAt: String,
    updatedAt: String
)

object EntityViewModel {
  def from(e: Entity): EntityViewModel =
    EntityViewModel(e.id.value, e.name, e.entityType.code, e.createdAt.toString, e.updatedAt.toString)
}
