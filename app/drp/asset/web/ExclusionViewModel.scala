package drp.asset.web

import drp.asset.domain.Exclusion

/** Read model for Twirl — flat primitives only. */
final case class ExclusionViewModel(
    id: Long,
    entityId: Long,
    value: String,
    matchType: String,
    reason: String,
    createdBy: String,
    createdAt: String,
    updatedAt: String
)

object ExclusionViewModel {
  def from(x: Exclusion): ExclusionViewModel =
    ExclusionViewModel(x.id.value, x.entityId.value, x.value, x.matchType.code, x.reason.code, x.createdBy, x.createdAt.toString, x.updatedAt.toString)
}
