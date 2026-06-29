package drp.asset.web

import play.api.data.Form
import play.api.data.Forms._

/** Write model for the entity create/edit form. Blank handling is delegated to the domain smart ctor. */
final case class EntityFormData(name: String, entityType: String)

object EntityFormData {
  val form: Form[EntityFormData] = Form(
    mapping(
      "name"       -> text,
      "entityType" -> text
    )(EntityFormData.apply)(EntityFormData.unapply)
  )
}
