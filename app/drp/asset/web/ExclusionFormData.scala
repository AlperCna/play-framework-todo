package drp.asset.web

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}

/** Form payload for registering an exclusion. `entityId` comes from the route path, not the form. */
final case class ExclusionFormData(value: String, matchType: String, reason: String)

object ExclusionFormData {

  val form: Form[ExclusionFormData] = Form(
    mapping(
      "value"     -> nonEmptyText,
      "matchType" -> nonEmptyText,
      "reason"    -> nonEmptyText
    )(ExclusionFormData.apply)(ExclusionFormData.unapply)
  )
}
