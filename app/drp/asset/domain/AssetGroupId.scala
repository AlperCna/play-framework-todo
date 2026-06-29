package drp.asset.domain

/**
 * Strongly-typed asset-group id (0 = unsaved). Declared here so `Asset` can carry an optional group
 * reference now; the `AssetGroup` entity + its CRUD arrive with US4.
 */
final case class AssetGroupId(value: Long) extends AnyVal
