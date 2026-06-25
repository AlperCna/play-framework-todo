package controllers

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import actions.{AuthenticatedAction, AuthenticatedRequest}
import domain.category.Category
import domain.common.DomainError
import forms.CategoryFormData
import pagination.PageRequest
import services.CategoryService

/**
 * Kategoriler uzerinde CRUD islemlerini yoneten controller.
 *
 * [[TaskItemController]] ile ayni desen: `authAction.async` ile giris zorunlu +
 * `request.user` (CurrentUser). Her kullanici yalniz KENDI kategorilerini
 * gorur/degistirir; baskasinin kategorisine erisim 404.
 */
@Singleton
class CategoryController @Inject() (
    service: CategoryService,
    authAction: AuthenticatedAction,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  /**
   * READ (liste) — yalniz current user'in kategorileri, SAYFALANMIS.
   * [[TaskItemController.list]] ile ayni desen (clamp + aralik-disi sayfada son
   * sayfaya redirect). Bkz. `pagination` paketi.
   */
  def list(page: Int, size: Int): Action[AnyContent] = authAction.async { implicit request =>
    val pageRequest = PageRequest.from(page, size)
    service.listByUser(request.user.id, pageRequest).map { result =>
      if (result.totalCount > 0 && result.pageNumber > result.totalPages)
        Redirect(routes.CategoryController.list(result.totalPages, result.pageSize))
      else
        Ok(views.html.categories.list(result))
    }
  }

  /** CREATE (form). */
  def createForm(): Action[AnyContent] = authAction { implicit request =>
    Ok(views.html.categories.form(CategoryFormData.form, routes.CategoryController.create(), request.messages("category.form.new")))
  }

  /** CREATE (kaydet) — sahip current user. */
  def create(): Action[AnyContent] = authAction.async { implicit request =>
    val heading = request.messages("category.form.new")
    val postUrl = routes.CategoryController.create()
    CategoryFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.categories.form(formWithErrors, postUrl, heading))),
      data =>
        service.create(data.name, data.description, request.user.id).map {
          case Right(_) =>
            Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.created"))
          case Left(err) =>
            BadRequest(views.html.categories.form(CategoryFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  /** UPDATE (form). */
  def editForm(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedCategory(id) { category =>
      val filled = CategoryFormData.form.fill(CategoryFormData(category.name, category.description))
      Future.successful(
        Ok(views.html.categories.form(filled, routes.CategoryController.update(id), request.messages("category.form.edit", id)))
      )
    }
  }

  /** UPDATE (kaydet). */
  def update(id: Long): Action[AnyContent] = authAction.async { implicit request =>
    withOwnedCategory(id) { _ =>
      val heading = request.messages("category.form.edit", id)
      val postUrl = routes.CategoryController.update(id)
      CategoryFormData.form.bindFromRequest().fold(
        formWithErrors => Future.successful(BadRequest(views.html.categories.form(formWithErrors, postUrl, heading))),
        data =>
          service.update(id, data.name, data.description).map {
            case Right(_) =>
              Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.updated"))
            case Left(DomainError.NotFound(_, _)) =>
              NotFound(request.messages("category.notFound", id))
            case Left(err) =>
              BadRequest(views.html.categories.form(CategoryFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
          }
      )
    }
  }

  /** DELETE (soft). */
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

  /** Sahiplik guard'i: kategori yalnizca current user'a aitse `f`'e verir; aksi halde 404. */
  private def withOwnedCategory(id: Long)(f: Category => Future[Result])(
      implicit request: AuthenticatedRequest[AnyContent]
  ): Future[Result] =
    service.get(id).flatMap {
      case Some(category) if category.userId == request.user.id => f(category)
      case _                                                    => Future.successful(NotFound(request.messages("category.notFound", id)))
    }
}
