package controllers

import javax.inject._

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

import models.TodoFormData
import repositories.TodoRepository

/**
 * Todo'lar uzerinde temel CRUD islemlerini yoneten controller.
 *
 * `MessagesAbstractController`'dan turuyoruz cunku Twirl form helper'lari
 * (orn. `@helper.inputText`) hata mesajlarini gostermek icin ortulu (implicit)
 * bir `MessagesProvider` ister; bu taban sinif onu istek uzerinden saglar.
 *
 * `TodoRepository` constructor uzerinden enjekte ediliyor: controller somut
 * implementasyonu degil, sadece arayuzu bilir.
 */
@Singleton
class TodoController @Inject()(
    repository: TodoRepository,
    cc: MessagesControllerComponents
) extends MessagesAbstractController(cc) {

  /**
   * Form tanimi: kullanicidan gelen ham veriyi dogrulayip `TodoFormData`'ya
   * donusturur.
   *   - title: bos olamaz (nonEmptyText).
   *   - completed: checkbox'tan gelen boolean.
   */
  private val todoForm: Form[TodoFormData] = Form(
    mapping(
      "title" -> nonEmptyText,
      "completed" -> boolean
    )(TodoFormData.apply)(TodoFormData.unapply)
  )

  /** READ (liste): tum todo'lari listeler. */
  def list(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.todos.list(repository.list()))
  }

  /** CREATE (form): yeni todo olusturma formunu gosterir. */
  def createForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.todos.form(todoForm, routes.TodoController.create(), request.messages("todo.form.new")))
  }

  /** CREATE (kaydet): formu dogrular ve yeni todo'yu olusturur. */
  def create(): Action[AnyContent] = Action { implicit request =>
    todoForm.bindFromRequest().fold(
      // Dogrulama hatasi: formu hatalariyla birlikte tekrar goster.
      formWithErrors =>
        BadRequest(views.html.todos.form(formWithErrors, routes.TodoController.create(), request.messages("todo.form.new"))),
      // Basarili: kaydet ve listeye yonlendir.
      data => {
        repository.create(data.title, data.completed)
        Redirect(routes.TodoController.list()).flashing("success" -> request.messages("todo.created"))
      }
    )
  }

  /** UPDATE (form): var olan todo'yu duzenleme formunu gosterir. */
  def editForm(id: Long): Action[AnyContent] = Action { implicit request =>
    repository.get(id) match {
      case Some(todo) =>
        // Mevcut degerlerle formu onceden doldur.
        val filledForm = todoForm.fill(TodoFormData(todo.title, todo.completed))
        Ok(views.html.todos.form(filledForm, routes.TodoController.update(id), request.messages("todo.form.edit", id)))
      case None =>
        NotFound(request.messages("todo.notFound", id))
    }
  }

  /** UPDATE (kaydet): formu dogrular ve todo'yu gunceller. */
  def update(id: Long): Action[AnyContent] = Action { implicit request =>
    todoForm.bindFromRequest().fold(
      formWithErrors =>
        BadRequest(views.html.todos.form(formWithErrors, routes.TodoController.update(id), request.messages("todo.form.edit", id))),
      data =>
        repository.update(id, data.title, data.completed) match {
          case Some(_) =>
            Redirect(routes.TodoController.list()).flashing("success" -> request.messages("todo.updated"))
          case None =>
            NotFound(request.messages("todo.notFound", id))
        }
    )
  }

  /** DELETE: todo'yu siler. */
  def delete(id: Long): Action[AnyContent] = Action { implicit request =>
    if (repository.delete(id)) {
      Redirect(routes.TodoController.list()).flashing("success" -> request.messages("todo.deleted"))
    } else {
      NotFound(request.messages("todo.notFound", id))
    }
  }
}
