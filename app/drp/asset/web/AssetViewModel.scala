package drp.asset.web

import drp.asset.domain.Asset

/** Read model for Twirl — flat primitives only. */
final case class AssetViewModel(
    id: Long,
    entityId: Long,
    assetType: String,
    value: String,
    homepageUrl: String,
    loginPageUrl: String,
    logoRef: String,
    faviconRef: String,
    isActive: Boolean,
    createdAt: String,
    updatedAt: String
)

object AssetViewModel {
  def from(a: Asset): AssetViewModel =
    AssetViewModel(
      a.id.value,
      a.entityId.value,
      a.assetType.code,
      a.value,
      a.metadata.homepageUrl.getOrElse(""),
      a.metadata.loginPageUrl.getOrElse(""),
      a.metadata.logoRef.getOrElse(""),
      a.metadata.faviconRef.getOrElse(""),
      a.isActive,
      a.createdAt.toString,
      a.updatedAt.toString
    )
}
