package controllers

import javax.inject._

import play.api.data.Form
import play.api.data.Forms.{longNumber, single}
import play.api.mvc._

import actions.{AuthenticatedAction, AuthenticatedRequest}
import domain.common.DomainError
import domain.task.TaskItem
import forms.TaskItemFormData
import services.{CategoryService, TaskItemService}

/**
 * Gorevler (TaskItem) uzerinde CRUD + tamamla/yeniden-ac + kategori atama.
 *
 * INCE controller: repository'ye DEGIL servislere baglidir. Tum uclar `Action`
 * yerine `authAction` kullanir: giris zorunlu olur ve `request.user` (CurrentUser)
 * hazir gelir. Her kullanici yalniz KENDI gorevlerini gorur/degistirir; baskasinin
 * gorevine erisim 404 ile engellenir (sahiplik guard'i).
 */
@Singleton
class TaskItemController @Inject() (
    service: TaskItemService,
    categoryService: CategoryService,
    authAction: AuthenticatedAction,
    cc: MessagesControllerComponents
) extends MessagesAbstractController(cc) {

  /** Kategori atama icin tek alanli kucuk form (dropdown'dan gelen categoryId). */
  private val assignForm: Form[Long] = Form(single("categoryId" -> longNumber))

  /** READ (liste) — yalniz current user'in gorevleri. */
  def list(): Action[AnyContent] = authAction { implicit request =>
    Ok(views.html.tasks.list(service.listByUser(request.user.id)))
  }

  /** CREATE (form). */
  def createForm(): Action[AnyContent] = authAction { implicit request =>
    Ok(views.html.tasks.form(TaskItemFormData.form, routes.TaskItemController.create(), request.messages("task.form.new")))
  }

  /** CREATE (kaydet) — sahip current user. */
  def create(): Action[AnyContent] = authAction { implicit request =>
    val heading = request.messages("task.form.new")
    val postUrl = routes.TaskItemController.create()
    TaskItemFormData.form.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.tasks.form(formWithErrors, postUrl, heading)),
      data =>
        service.create(data.title, data.description, data.priority, data.dueDate, request.user.id) match {
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
  def editForm(id: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { task =>
      val filled = TaskItemFormData.form.fill(
        TaskItemFormData(task.title, task.description, task.priority, task.dueDate)
      )
      val assigned = service.categoriesOf(id)
      val available = categoryService.listByUser(request.user.id).filterNot(c => assigned.exists(_.id == c.id))
      Ok(views.html.tasks.form(filled, routes.TaskItemController.update(id), request.messages("task.form.edit", id), Some(id), assigned, available))
    }
  }

  /** UPDATE (kaydet). */
  def update(id: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { _ =>
      val heading = request.messages("task.form.edit", id)
      val postUrl = routes.TaskItemController.update(id)
      TaskItemFormData.form.bindFromRequest().fold(
        formWithErrors => BadRequest(views.html.tasks.form(formWithErrors, postUrl, heading)),
        data =>
          service.update(id, data.title, data.description, data.priority, data.dueDate) match {
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
  def complete(id: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.complete(id), id, "task.completed") }
  }

  /** Gorevi yeniden ac. */
  def reopen(id: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.reopen(id), id, "task.reopened") }
  }

  /** Gorevi (soft) sil. */
  def delete(id: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.delete(id), id, "task.deleted") }
  }

  /** Gorevi bir kategoriye atar (gorev edit sayfasindaki dropdown'dan). */
  def assignToCategory(id: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { _ =>
      val back = Redirect(routes.TaskItemController.editForm(id))
      assignForm.bindFromRequest().fold(
        _ => back.flashing("error" -> request.messages("task.category.invalid")),
        categoryId =>
          // Hedef kategori de kullaniciya ait olmali (baskasinin kategorisine atama engellenir).
          if (!categoryService.get(categoryId).exists(_.userId == request.user.id))
            back.flashing("error" -> request.messages("task.category.invalid"))
          else
            service.assignToCategory(id, categoryId) match {
              case Right(_) => back.flashing("success" -> request.messages("task.category.assigned"))
              case Left(err) => back.flashing("error" -> request.messages(err.code))
            }
      )
    }
  }

  /** Gorevi bir kategoriden cikarir; edit sayfasina geri doner. */
  def removeFromCategory(id: Long, categoryId: Long): Action[AnyContent] = authAction { implicit request =>
    withOwnedTask(id) { _ =>
      val back = Redirect(routes.TaskItemController.editForm(id))
      service.removeFromCategory(id, categoryId) match {
        case Right(_) => back.flashing("success" -> request.messages("task.category.removed"))
        case Left(err) => back.flashing("error" -> request.messages(err.code))
      }
    }
  }

  /**
   * Sahiplik guard'i: id'li gorevi yalnizca current user'a aitse `f`'e verir;
   * aksi halde (yok ya da baskasina ait) 404. Boylece baskasinin gorevine erisim
   * sizdirilmaz.
   */
  private def withOwnedTask(id: Long)(f: TaskItem => Result)(implicit request: AuthenticatedRequest[AnyContent]): Result =
    service.get(id).filter(_.userId.contains(request.user.id)) match {
      case Some(task) => f(task)
      case None => NotFound(request.messages("task.notFound", id))
    }

  /**
   * Liste sayfasi action'lari (complete/reopen/delete) icin ortak akis:
   * basari -> flash success; NotFound -> 404; diger domain hatasi -> flash error.
   */
  private def redirectAfterAction(
      result: Either[DomainError, _],
      id: Long,
      successKey: String
  )(implicit request: AuthenticatedRequest[AnyContent]): Result =
    result match {
      case Right(_) =>
        Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages(successKey))
      case Left(DomainError.NotFound(_, _)) =>
        NotFound(request.messages("task.notFound", id))
      case Left(err) =>
        Redirect(routes.TaskItemController.list()).flashing("error" -> request.messages(err.code))
    }
}
