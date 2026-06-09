package forms

import play.api.data.Form
import play.api.data.Forms._

/** Giris (login) formundan gelen veri. */
case class LoginFormData(email: String, password: String)

object LoginFormData {
  val form: Form[LoginFormData] = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText
    )(LoginFormData.apply)(LoginFormData.unapply)
  )
}
