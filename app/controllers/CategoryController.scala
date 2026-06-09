package controllers

import javax.inject._

import play.api.mvc._

import domain.common.DomainError
import forms.CategoryFormData
import repositories.UserRepository
import services.CategoryService

/**
 * Kategoriler uzerinde CRUD islemlerini yoneten controller.
 *
 * [[TaskItemController]] ile ayni desen: ince controller, [[CategoryService]]'e
 * baglanir; servisten donen `Either[DomainError, T]`'yi HTTP'ye cevirir.
 * Tamamla/yeniden-ac gibi durum gecisleri yoktur; sadece CRUD.
 */
@Singleton
class CategoryController @Inject() (
    service: CategoryService,
    userRepo: UserRepository,
    cc: MessagesControllerComponents
) extends MessagesAbstractController(cc) {

  /** Create icin sahip kullanici (seed kullaniciyi izler; Category.create userId>0 ister). */
  private def defaultUserId: Long =
    userRepo.list().headOption.map(_.id).getOrElse(1L)

  /** READ (liste). */
  def list(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.categories.list(service.list()))
  }

  /** CREATE (form). */
  def createForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.categories.form(CategoryFormData.form, routes.CategoryController.create(), request.messages("category.form.new")))
  }

  /** CREATE (kaydet). */
  def create(): Action[AnyContent] = Action { implicit request =>
    val heading = request.messages("category.form.new")
    val postUrl = routes.CategoryController.create()
    CategoryFormData.form.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.categories.form(formWithErrors, postUrl, heading)),
      data =>
        service.create(data.name, data.description, defaultUserId) match {
          case Right(_) =>
            Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.created"))
          case Left(err) =>
            BadRequest(views.html.categories.form(CategoryFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  /** UPDATE (form). */
  def editForm(id: Long): Action[AnyContent] = Action { implicit request =>
    service.get(id) match {
      case Some(category) =>
        val filled = CategoryFormData.form.fill(CategoryFormData(category.name, category.description))
        Ok(views.html.categories.form(filled, routes.CategoryController.update(id), request.messages("category.form.edit", id)))
      case None =>
        NotFound(request.messages("category.notFound", id))
    }
  }

  /** UPDATE (kaydet). */
  def update(id: Long): Action[AnyContent] = Action { implicit request =>
    val heading = request.messages("category.form.edit", id)
    val postUrl = routes.CategoryController.update(id)
    CategoryFormData.form.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.categories.form(formWithErrors, postUrl, heading)),
      data =>
        service.update(id, data.name, data.description) match {
          case Right(_) =>
            Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.updated"))
          case Left(DomainError.NotFound(_, _)) =>
            NotFound(request.messages("category.notFound", id))
          case Left(err) =>
            BadRequest(views.html.categories.form(CategoryFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  /** DELETE (soft). */
  def delete(id: Long): Action[AnyContent] = Action { implicit request =>
    service.delete(id) match {
      case Right(_) =>
        Redirect(routes.CategoryController.list()).flashing("success" -> request.messages("category.deleted"))
      case Left(_) =>
        NotFound(request.messages("category.notFound", id))
    }
  }
}
