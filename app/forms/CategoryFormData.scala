package forms

import play.api.data.Form
import play.api.data.Forms._

/**
 * Kategori form'undan gelen veriyi tasiyan DTO.
 *
 * Domain entity'sinden ayri: kullanici `id`, `userId` ve audit girmez.
 * Her iki alan da zorunludur (Category'de aciklama da zorunlu — spec).
 */
case class CategoryFormData(name: String, description: String)

object CategoryFormData {

  /**
   * Form tanimi (sozdizimsel dogrulama). Is kurali (trim/bos) domain'de de
   * korunur. Ozel formatter gerekmez; iki alan da String.
   */
  val form: Form[CategoryFormData] = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )(CategoryFormData.apply)(CategoryFormData.unapply)
  )
}
