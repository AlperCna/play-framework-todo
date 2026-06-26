package todo.task.web

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import play.api.data.Form
import play.api.data.Forms.{longNumber, single}
import play.api.libs.json.Json
import play.api.mvc._

import todo.category.application.CategoryService
import todo.shared.web.AuthenticatedAction
import todo.shared.web.AuthenticatedRequest
import todo.shared.domain.DomainError
import todo.task.application.TaskItemService
import todo.task.domain.TaskItem

@Singleton
class TaskItemController @Inject() (
    service: TaskItemService,
    categoryService: CategoryService,
    authAction: AuthenticatedAction,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private val assignForm: Form[Long] = Form(single("categoryId" -> longNumber))

  def list(page: Int, size: Int): Action[AnyContent] = authAction.async { implicit request =>
    import todo.shared.application.PageRequest
    val pageRequest = PageRequest.from(page, size)
    val pageF         = service.listByUser(request.user.id, pageRequest)
    val hasCompletedF = service.hasCompletedByUser(request.user.id)
    for {
      result       <- pageF
      hasCompleted <- hasCompletedF
    } yield {
      if (result.totalCount > 0 && result.pageNumber > result.totalPages)
        Redirect(routes.TaskItemController.list(result.totalPages, result.pageSize))
      else
        Ok(todo.task.web.views.html.list(result, hasCompleted))
    }
  }

  def createForm(): Action[AnyContent] = authAction { implicit request =>
    Ok(todo.task.web.views.html.form(TaskItemFormData.form, routes.TaskItemController.create(), request.messages("task.form.new")))
  }

  def create(): Action[AnyContent] = authAction.async { implicit request =>
    val heading = request.messages("task.form.new")
    val postUrl = routes.TaskItemController.create()
    TaskItemFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(todo.task.web.views.html.form(formWithErrors, postUrl, heading))),
      data =>
        service.create(data.title, data.description, data.priority, data.dueDate, request.user.id).map {
          case Right(_) =>
            Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages("task.created"))
          case Left(err) =>
            BadRequest(todo.task.web.views.html.form(TaskItemFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  def editForm(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { task =>
      for {
        assigned  <- service.categoriesOf(id)
        available <- categoryService.listByUser(request.user.id).map(_.filterNot(c => assigned.exists(_.id == c.id)))
      } yield {
        val filled = TaskItemFormData.form.fill(
          TaskItemFormData(task.title, task.description, task.priority, task.dueDate)
        )
        Ok(todo.task.web.views.html.form(filled, routes.TaskItemController.update(id), request.messages("task.form.edit", id), Some(id), assigned, available))
      }
    }
  }

  def update(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ =>
      val heading = request.messages("task.form.edit", id)
      val postUrl = routes.TaskItemController.update(id)
      TaskItemFormData.form.bindFromRequest().fold(
        formWithErrors => Future.successful(BadRequest(todo.task.web.views.html.form(formWithErrors, postUrl, heading))),
        data =>
          service.update(id, data.title, data.description, data.priority, data.dueDate).map {
            case Right(_) =>
              Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages("task.updated"))
            case Left(DomainError.NotFound(_, _)) =>
              NotFound(request.messages("task.notFound", id))
            case Left(err) =>
              BadRequest(todo.task.web.views.html.form(TaskItemFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
          }
      )
    }
  }

  def complete(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.complete(id), id, "task.completed") }
  }

  def reopen(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.reopen(id), id, "task.reopened") }
  }

  def delete(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ => redirectAfterAction(service.delete(id), id, "task.deleted") }
  }

  def assignToCategory(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ =>
      val back = Redirect(routes.TaskItemController.editForm(id))
      assignForm.bindFromRequest().fold(
        _ => Future.successful(back.flashing("error" -> request.messages("task.category.invalid"))),
        categoryId =>
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

  def removeFromCategory(id: Long, categoryId: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTask(id) { _ =>
      val back = Redirect(routes.TaskItemController.editForm(id))
      service.removeFromCategory(id, categoryId).map {
        case Right(_)  => back.flashing("success" -> request.messages("task.category.removed"))
        case Left(err) => back.flashing("error" -> request.messages(err.code))
      }
    }
  }

  def completeJson(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTaskJson(id) {
      service.complete(id).map(toggleJson(_, "task.completed"))
    }
  }

  def reopenJson(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedTaskJson(id) {
      service.reopen(id).map(toggleJson(_, "task.reopened"))
    }
  }

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

  private def withOwnedTaskJson(id: Long)(block: => Future[Result])(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] =
    service.get(id).flatMap {
      case Some(task) if task.userId.contains(request.user.id) => block
      case _ => Future.successful(NotFound(Json.obj("error" -> request.messages("task.notFound", id))))
    }

  private def withOwnedTask(id: Long)(f: TaskItem => Future[Result])(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] =
    service.get(id).flatMap {
      case Some(task) if task.userId.contains(request.user.id) => f(task)
      case _                                                   => Future.successful(NotFound(request.messages("task.notFound", id)))
    }

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
