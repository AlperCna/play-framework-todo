package todo.task.web

import java.time.LocalDate

import scala.util.Try

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.FormError
import play.api.data.format.Formatter

import todo.shared.domain.Priority

case class TaskItemFormData(
    title: String,
    description: Option[String],
    priority: Priority,
    dueDate: Option[LocalDate]
)

object TaskItemFormData {

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

  val form: Form[TaskItemFormData] = Form(
    mapping(
      "title" -> nonEmptyText,
      "description" -> optional(text),
      "priority" -> of[Priority],
      "dueDate" -> optional(localDate("yyyy-MM-dd"))
    )(TaskItemFormData.apply)(TaskItemFormData.unapply)
  )
}
