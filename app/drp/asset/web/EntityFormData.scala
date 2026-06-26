package drp.asset.web

import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}

/** Form payload for registering a protected entity. */
final case class EntityFormData(name: String, entityType: String)

object EntityFormData {

  val form: Form[EntityFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "type" -> nonEmptyText
    )(EntityFormData.apply)(EntityFormData.unapply)
  )
}
