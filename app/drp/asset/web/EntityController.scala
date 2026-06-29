package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import drp.asset.application.{AssetService, EntityService}
import drp.asset.domain.EntityId
import drp.shared.application.PageRequest
import drp.shared.domain.DomainError

/** Thin web layer for entities — binds the form, calls the service, maps results to HTTP + view-model. */
@Singleton
class EntityController @Inject() (
    cc: MessagesControllerComponents,
    service: EntityService,
    assetService: AssetService
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private val listCall = routes.EntityController.list()

  def list(page: Int) = Action.async { implicit request =>
    service.list(PageRequest.of(page)).map(p => Ok(views.html.entitiesList(p.map(EntityViewModel.from))))
  }

  def newForm = Action { implicit request =>
    Ok(views.html.entityForm(EntityFormData.form, routes.EntityController.create(), request.messages("drp.entity.new")))
  }

  def create = Action.async { implicit request =>
    EntityFormData.form.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(views.html.entityForm(formWithErrors, routes.EntityController.create(), request.messages("drp.entity.new")))),
      data =>
        service.create(data.name, data.entityType).map {
          case Right(_) => Redirect(listCall).flashing("success" -> request.messages("drp.entity.created"))
          case Left(err) =>
            BadRequest(views.html.entityForm(EntityFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.EntityController.create(), request.messages("drp.entity.new")))
        }
    )
  }

  def view(id: Long) = Action.async { implicit request =>
    val eid = EntityId(id)
    service.get(eid).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.entity.notFound")))
      case Some(e) =>
        assetService.listByEntity(eid).map { assets =>
          Ok(views.html.entityView(EntityViewModel.from(e), assets.map(AssetViewModel.from)))
        }
    }
  }

  def editForm(id: Long) = Action.async { implicit request =>
    service.get(EntityId(id)).map {
      case Some(e) =>
        Ok(views.html.entityForm(EntityFormData.form.fill(EntityFormData(e.name, e.entityType.code)), routes.EntityController.update(id), request.messages("drp.entity.edit")))
      case None => NotFound(request.messages("drp.entity.notFound"))
    }
  }

  def update(id: Long) = Action.async { implicit request =>
    EntityFormData.form.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(views.html.entityForm(formWithErrors, routes.EntityController.update(id), request.messages("drp.entity.edit")))),
      data =>
        service.update(EntityId(id), data.name, data.entityType).map {
          case Right(_)                            => Redirect(listCall).flashing("success" -> request.messages("drp.entity.updated"))
          case Left(DomainError.EntityNotFound(_)) => NotFound(request.messages("drp.entity.notFound"))
          case Left(err) =>
            BadRequest(views.html.entityForm(EntityFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.EntityController.update(id), request.messages("drp.entity.edit")))
        }
    )
  }
}
