package controllers

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import play.api.data.Form
import play.api.data.Forms.{longNumber, single}
import play.api.libs.json.Json
import play.api.mvc._

import actions.{AuthenticatedAction, AuthenticatedRequest}
import domain.common.DomainError
import domain.task.TaskItem
import forms.TaskItemFormData
import services.{CategoryService, TaskItemService}

/**
 * Gorevler (TaskItem) uzerinde CRUD + tamamla/yeniden-ac + kategori atama.
 *
 * INCE controller: repository'ye DEGIL servislere baglidir. Servisler artik
 * `Future` dondugu icin uclar `authAction.async` ile yazilir; sahiplik guard'i
 * ve ortak yardimcilar da `Future[Result]` doner.
 */
@Singleton
class TaskItemController @Inject() (
    service: TaskItemService,
    categoryService: CategoryService,
    authAction: AuthenticatedAction,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  /** Kategori atama icin tek alanli kucuk form (dropdown'dan gelen categoryId). */
  private val assignForm: Form[Long] = Form(single("categoryId" -> longNumber))

  /** READ (liste) — yalniz current user'in gorevleri. */
  def list(): Action[AnyContent] = authAction.async { implicit request =>
    service.listByUser(request.user.id).map(tasks => Ok(views.html.tasks.list(tasks)))
  }

  /** CREATE (form). */
  def createForm(): Action[AnyContent] = authAction { implicit request =>
    Ok(views.html.tasks.form(TaskItemFormData.form, routes.TaskItemController.create(), request.messages("task.form.new")))
  }

  /** CREATE (kaydet) — sahip current user. */
  def create(): Action[AnyContent] = authAction.async { implicit request =>
    val heading = request.messages("task.form.new")
    val postUrl = routes.TaskItemController.create()
    TaskItemFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.tasks.form(formWithErrors, postUrl, heading))),
      data =>
        service.create(data.title, data.description, data.priority, data.dueDate, request.user.id).map {
          case Right(_) =>
            Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages("task.created"))
          case Left(err) =>
            BadRequest(views.html.tasks.form(TaskItemFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  /**
   * UPDATE (form). Edit modunda gorevin atanmis ve (kullaniciya ait) eklenebilir
   * kategorilerini de view'a gecirir.
   */
  def editForm(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { task =>
      for {
        assigned  <- service.categoriesOf(id)
        available <- categoryService.listByUser(request.user.id).map(_.filterNot(c => assigned.exists(_.id == c.id)))
      } yield {
        val filled = TaskItemFormData.form.fill(
          TaskItemFormData(task.title, task.description, task.priority, task.dueDate)
        )
        Ok(views.html.tasks.form(filled, routes.TaskItemController.update(id), request.messages("task.form.edit", id), Some(id), assigned, available))
      }
    }
  }

  /** UPDATE (kaydet). */
  def update(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ =>
      val heading = request.messages("task.form.edit", id)
      val postUrl = routes.TaskItemController.update(id)
      TaskItemFormData.form.bindFromRequest().fold(
        formWithErrors => Future.successful(BadRequest(views.html.tasks.form(formWithErrors, postUrl, heading))),
        data =>
          service.update(id, data.title, data.description, data.priority, data.dueDate).map {
            case Right(_) =>
              Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages("task.updated"))
            case Left(DomainError.NotFound(_, _)) =>
              NotFound(request.messages("task.notFound", id))
            case Left(err) =>
              BadRequest(views.html.tasks.form(TaskItemFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
          }
      )
    }
  }

  /** Gorevi tamamla. */
  def complete(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.complete(id), id, "task.completed") }
  }

  /** Gorevi yeniden ac. */
  def reopen(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.reopen(id), id, "task.reopened") }
  }

  /** Gorevi (soft) sil. */
  def delete(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.delete(id), id, "task.deleted") }
  }

  /** Gorevi bir kategoriye atar (gorev edit sayfasindaki dropdown'dan). */
  def assignToCategory(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ =>
      val back = Redirect(routes.TaskItemController.editForm(id))
      assignForm.bindFromRequest().fold(
        _ => Future.successful(back.flashing("error" -> request.messages("task.category.invalid"))),
        categoryId =>
          // Hedef kategori de kullaniciya ait olmali (baskasinin kategorisine atama engellenir).
          categoryService.get(categoryId).flatMap { maybeCategory =>
            if (!maybeCategory.exists(_.userId == request.user.id))
              Future.successful(back.flashing("error" -> request.messages("task.category.invalid")))
            else
              service.assignToCategory(id, categoryId).map {
                case Right(_)  => back.flashing("success" -> request.messages("task.category.assigned"))
                case Left(err) => back.flashing("error" -> request.messages(err.code))
              }
          }
      )
    }
  }

  /** Gorevi bir kategoriden cikarir; edit sayfasina geri doner. */
  def removeFromCategory(id: Long, categoryId: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ =>
      val back = Redirect(routes.TaskItemController.editForm(id))
      service.removeFromCategory(id, categoryId).map {
        case Right(_)  => back.flashing("success" -> request.messages("task.category.removed"))
        case Left(err) => back.flashing("error" -> request.messages(err.code))
      }
    }
  }

  // --- AJAX (JSON) uclari: liste sayfasinda sayfa yenilenmeden tamamla/yeniden-ac ---

  /** `complete` ile ayni is; ama HTML redirect yerine JSON doner (fetch icin). */
  def completeJson(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTaskJson(id) {
      service.complete(id).map(toggleJson(_, "task.completed"))
    }
  }

  /** `reopen` ile ayni is; ama HTML redirect yerine JSON doner (fetch icin). */
  def reopenJson(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTaskJson(id) {
      service.reopen(id).map(toggleJson(_, "task.reopened"))
    }
  }

  /**
   * Domain sonucunu JSON'a cevirir:
   *   - basari -> 200 + { id, isCompleted, message }
   *   - is kurali ihlali (orn. gecmis tarihli gorevi tamamlama) -> 400 + { error }
   */
  private def toggleJson(result: Either[DomainError, TaskItem], successKey: String)(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Result =
    result match {
      case Right(task) =>
        Ok(Json.obj(
          "id"          -> task.id,
          "isCompleted" -> task.isCompleted,
          "message"     -> request.messages(successKey)
        ))
      case Left(err) =>
        BadRequest(Json.obj("error" -> request.messages(err.code)))
    }

  /** [[withOwnedTask]]'in JSON karsiligi: gorev yok ya da baskasina aitse 404 + { error } (JSON). */
  private def withOwnedTaskJson(id: Long)(block: => Future[Result])(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] =
    service.get(id).flatMap {
      case Some(task) if task.userId.contains(request.user.id) => block
      case _ => Future.successful(NotFound(Json.obj("error" -> request.messages("task.notFound", id))))
    }

  /**
   * Sahiplik guard'i: id'li gorevi yalnizca current user'a aitse `f`'e verir;
   * aksi halde (yok ya da baskasina ait) 404. Boylece baskasinin gorevine erisim
   * sizdirilmaz.
   */
  private def withOwnedTask(id: Long)(f: TaskItem => Future[Result])(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] =
    service.get(id).flatMap {
      case Some(task) if task.userId.contains(request.user.id) => f(task)
      case _                                                   => Future.successful(NotFound(request.messages("task.notFound", id)))
    }

  /**
   * Liste sayfasi action'lari (complete/reopen/delete) icin ortak akis:
   * basari -> flash success; NotFound -> 404; diger domain hatasi -> flash error.
   */
  private def redirectAfterAction(
      result: Future[Either[DomainError, _]],
      id: Long,
      successKey: String
  )(implicit request: AuthenticatedRequest[AnyContent]): Future[Result] =
    result.map {
      case Right(_) =>
        Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages(successKey))
      case Left(DomainError.NotFound(_, _)) =>
        NotFound(request.messages("task.notFound", id))
      case Left(err) =>
        Redirect(routes.TaskItemController.list()).flashing("error" -> request.messages(err.code))
    }
}
