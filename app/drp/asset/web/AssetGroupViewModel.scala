package drp.asset.web

import drp.asset.domain.AssetGroup

/** Read model for Twirl — flat primitives only. */
final case class AssetGroupViewModel(id: Long, entityId: Long, name: String, createdAt: String, updatedAt: String)

object AssetGroupViewModel {
  def from(g: AssetGroup): AssetGroupViewModel =
    AssetGroupViewModel(g.id.value, g.entityId.value, g.name, g.createdAt.toString, g.updatedAt.toString)
}
