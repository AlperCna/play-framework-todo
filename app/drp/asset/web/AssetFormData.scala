package drp.asset.web

import play.api.data.Form
import play.api.data.Forms._

import drp.asset.domain.{Asset, AssetMetadata}

/** Write model for the asset form — discrete reference fields; the system assembles metadata (FR-016). */
final case class AssetFormData(
    assetType: String,
    value: String,
    homepageUrl: String,
    loginPageUrl: String,
    logoRef: String,
    faviconRef: String
) {
  def metadata: AssetMetadata = AssetMetadata.of(homepageUrl, loginPageUrl, logoRef, faviconRef)
}

object AssetFormData {
  val form: Form[AssetFormData] = Form(
    mapping(
      "assetType"    -> text,
      "value"        -> text,
      "homepageUrl"  -> text,
      "loginPageUrl" -> text,
      "logoRef"      -> text,
      "faviconRef"   -> text
    )(AssetFormData.apply)(AssetFormData.unapply)
  )

  def from(a: Asset): AssetFormData =
    AssetFormData(
      a.assetType.code,
      a.value,
      a.metadata.homepageUrl.getOrElse(""),
      a.metadata.loginPageUrl.getOrElse(""),
      a.metadata.logoRef.getOrElse(""),
      a.metadata.faviconRef.getOrElse("")
    )
}
