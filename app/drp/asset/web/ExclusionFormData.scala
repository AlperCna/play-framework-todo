package drp.asset.web

import play.api.data.Form
import play.api.data.Forms._

import drp.asset.domain.Exclusion

/** Write model for the exclusion form. */
final case class ExclusionFormData(value: String, matchType: String, reason: String)

object ExclusionFormData {
  val form: Form[ExclusionFormData] = Form(
    mapping(
      "value"     -> text,
      "matchType" -> text,
      "reason"    -> text
    )(ExclusionFormData.apply)(ExclusionFormData.unapply)
  )

  def from(x: Exclusion): ExclusionFormData =
    ExclusionFormData(x.value, x.matchType.code, x.reason.code)
}
