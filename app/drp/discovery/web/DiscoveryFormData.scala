package drp.discovery.web

import play.api.data.Form
import play.api.data.Forms._

/** Write model for the manual discovery intake form. */
final case class DiscoveryFormData(entityId: Long, assetId: Option[Long], value: String)

object DiscoveryFormData {
  val form: Form[DiscoveryFormData] = Form(
    mapping(
      "entityId" -> longNumber,
      "assetId"  -> optional(longNumber),
      "value"    -> nonEmptyText
    )(DiscoveryFormData.apply)(DiscoveryFormData.unapply)
  )
}
