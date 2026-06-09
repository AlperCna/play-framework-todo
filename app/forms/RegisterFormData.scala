package forms

import play.api.data.Form
import play.api.data.Forms._

/** Kayit (register) formundan gelen veri. */
case class RegisterFormData(email: String, password: String)

object RegisterFormData {
  val form: Form[RegisterFormData] = Form(
    mapping(
      "email" -> nonEmptyText,
      "password" -> nonEmptyText
    )(RegisterFormData.apply)(RegisterFormData.unapply)
  )
}
