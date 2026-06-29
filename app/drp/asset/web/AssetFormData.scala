package drp.asset.web

import play.api.data.Form
import play.api.data.Forms._

import drp.asset.domain.{Asset, AssetGroupId, AssetMetadata}

/** Write model for the asset form — discrete reference fields + optional group; system assembles metadata. */
final case class AssetFormData(
    assetType: String,
    value: String,
    homepageUrl: String,
    loginPageUrl: String,
    logoRef: String,
    faviconRef: String,
    assetGroupId: Option[Long]
) {
  def metadata: AssetMetadata = AssetMetadata.of(homepageUrl, loginPageUrl, logoRef, faviconRef)
  def groupId: Option[AssetGroupId] = assetGroupId.map(AssetGroupId)
}

object AssetFormData {
  val form: Form[AssetFormData] = Form(
    mapping(
      "assetType"    -> text,
      "value"        -> text,
      "homepageUrl"  -> text,
      "loginPageUrl" -> text,
      "logoRef"      -> text,
      "faviconRef"   -> text,
      "assetGroupId" -> optional(longNumber)
    )(AssetFormData.apply)(AssetFormData.unapply)
  )

  def from(a: Asset): AssetFormData =
    AssetFormData(
      a.assetType.code,
      a.value,
      a.metadata.homepageUrl.getOrElse(""),
      a.metadata.loginPageUrl.getOrElse(""),
      a.metadata.logoRef.getOrElse(""),
      a.metadata.faviconRef.getOrElse(""),
      a.assetGroupId.map(_.value)
    )
}
