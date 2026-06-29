package drp.asset.web

import play.api.data.Form
import play.api.data.Forms._

import drp.asset.domain.AssetGroup

/** Write model for the asset-group form. */
final case class AssetGroupFormData(name: String)

object AssetGroupFormData {
  val form: Form[AssetGroupFormData] = Form(
    mapping("name" -> text)(AssetGroupFormData.apply)(AssetGroupFormData.unapply)
  )

  def from(g: AssetGroup): AssetGroupFormData = AssetGroupFormData(g.name)
}
