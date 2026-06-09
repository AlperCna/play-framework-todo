package forms

import java.time.LocalDate

import scala.util.Try

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.FormError
import play.api.data.format.Formatter

import domain.common.Priority

/**
 * Form'dan gelen veriyi tasiyan yardimci model (DTO).
 *
 * Domain entity'sinden (`TaskItem`) ayri tutulur: kullanici `id`, audit, ya da
 * tamamlanma durumu girmez. `completed` BILEREK yoktur; tamamlama edit formundan
 * degil, listedeki Complete/Reopen butonlarindan (domain davranisi) yapilir.
 *
 * `priority` form katmaninda zaten `Priority`'ye cevrilmis gelir (companion
 * object'teki ozel `Formatter[Priority]` sayesinde).
 */
case class TaskItemFormData(
    title: String,
    description: Option[String],
    priority: Priority,
    dueDate: Option[LocalDate]
)

object TaskItemFormData {

  /**
   * Form alanindaki sayiyi (0/1/2) dogrudan `Priority`'ye baglayan ozel formatter.
   * Boylece ham sayi controller mantigina sizmaz; gecersiz sayi bir FORM hatasi
   * olur (sozdizimsel dogrulama).
   */
  private implicit val priorityFormatter: Formatter[Priority] = new Formatter[Priority] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Priority] =
      data
        .get(key)
        .flatMap(s => Try(s.trim.toInt).toOption)
        .flatMap(Priority.fromInt)
        .toRight(Seq(FormError(key, "error.invalidPriority")))

    override def unbind(key: String, value: Priority): Map[String, String] =
      Map(key -> value.value.toString)
  }

  /**
   * Form tanimi (sozdizimsel dogrulama). Controller bunu `TaskItemFormData.form`
   * olarak kullanir; is kurali dogrulamasi (High->dueDate, gecmis tarih) domain/
   * servis katmaninda yapilir.
   *   - title: bos olamaz
   *   - description: opsiyonel
   *   - priority: ozel formatter ile Priority'ye baglanir
   *   - dueDate: opsiyonel, gun bazli (yyyy-MM-dd)
   */
  val form: Form[TaskItemFormData] = Form(
    mapping(
      "title" -> nonEmptyText,
      "description" -> optional(text),
      "priority" -> of[Priority],
      "dueDate" -> optional(localDate("yyyy-MM-dd"))
    )(TaskItemFormData.apply)(TaskItemFormData.unapply)
  )
}
