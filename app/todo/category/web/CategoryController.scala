package todo.category.web

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import todo.category.application.CategoryService
import todo.category.domain.Category
import todo.shared.web.AuthenticatedAction
import todo.shared.web.AuthenticatedRequest
import todo.shared.domain.DomainError
import todo.shared.application.PageRequest

@Singleton
class CategoryController @Inject() (
    service: CategoryService,
    authAction: AuthenticatedAction,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def list(page: Int, size: Int): Action[AnyContent] = authAction.async { implicit request =>
    val pageRequest = PageRequest.from(page, size)
    service.listByUser(request.user.id, pageRequest).map { result =>
      if (result.totalCount > 0 && result.pageNumber > result.totalPages)
        Redirect(routes.CategoryController.list(result.totalPages, result.pageSize))
      else
        Ok(todo.category.web.views.html.list(result))
    }
  }

  def createForm(): Action[AnyContent] = authAction { implicit request =>
    Ok(todo.category.web.views.html.form(CategoryFormData.form, routes.CategoryController.create(), request.messages("category.form.new")))
  }

  def create(): Action[AnyContent] = authAction.async { implicit request =>
    val heading = request.messages("category.form.new")
    val postUrl = routes.CategoryController.create()
    CategoryFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(todo.category.web.views.html.form(formWithErrors, postUrl, heading))),
      data =>
        service.create(data.name, data.description, request.user.id).map {
          case Right(_) =>
            Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.created"))
          case Left(err) =>
            BadRequest(todo.category.web.views.html.form(CategoryFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  def editForm(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedCategory(id) { category =>
      val filled = CategoryFormData.form.fill(CategoryFormData(category.name, category.description))
      Future.successful(
        Ok(todo.category.web.views.html.form(filled, routes.CategoryController.update(id), request.messages("category.form.edit", id)))
      )
    }
  }

  def update(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedCategory(id) { _ =>
      val heading = request.messages("category.form.edit", id)
      val postUrl = routes.CategoryController.update(id)
      CategoryFormData.form.bindFromRequest().fold(
        formWithErrors => Future.successful(BadRequest(todo.category.web.views.html.form(formWithErrors, postUrl, heading))),
        data =>
          service.update(id, data.name, data.description).map {
            case Right(_) =>
              Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.updated"))
            case Left(DomainError.NotFound(_, _)) =>
              NotFound(request.messages("category.notFound", id))
            case Left(err) =>
              BadRequest(todo.category.web.views.html.form(CategoryFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
          }
      )
    }
  }

  def delete(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedCategory(id) { _ =>
      service.delete(id).map {
        case Right(_) =>
          Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.deleted"))
        case Left(_) =>
          NotFound(request.messages("category.notFound", id))
      }
    }
  }

  private def withOwnedCategory(id: Long)(f: Category => Future[Result])(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] =
    service.get(id).flatMap {
      case Some(category) if category.userId == request.user.id => f(category)
      case _                                                    => Future.successful(NotFound(request.messages("category.notFound", id)))
    }
}
