package todo.category.web

import play.api.data.Form
import play.api.data.Forms._

case class CategoryFormData(name: String, description: String)

object CategoryFormData {

  val form: Form[CategoryFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )(CategoryFormData.apply)(CategoryFormData.unapply)
  )
}
