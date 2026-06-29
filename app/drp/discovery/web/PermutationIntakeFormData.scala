package drp.discovery.web

import play.api.data.Form
import play.api.data.Forms._

/** Write model for the permutation intake confirmation form. Includes entityId as a hidden field. */
final case class PermutationIntakeFormData(entityId: Long, assetId: Long)

object PermutationIntakeFormData {
  val form: Form[PermutationIntakeFormData] = Form(
    mapping(
      "entityId" -> longNumber,
      "assetId"  -> longNumber
    )(PermutationIntakeFormData.apply)(PermutationIntakeFormData.unapply)
  )
}
