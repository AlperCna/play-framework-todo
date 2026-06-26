package todo.user.web

import play.api.data.Form
import play.api.data.Forms._

case class LoginFormData(email: String, password: String)

object LoginFormData {
  val form: Form[LoginFormData] = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText
    )(LoginFormData.apply)(LoginFormData.unapply)
  )
}
