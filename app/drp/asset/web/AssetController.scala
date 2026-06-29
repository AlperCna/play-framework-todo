package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import drp.asset.application.AssetService
import drp.asset.domain.{AssetId, EntityId}
import drp.shared.domain.DomainError

/** Thin web layer for assets (entity-scoped create; id-scoped edit). No delete. */
@Singleton
class AssetController @Inject() (cc: MessagesControllerComponents, service: AssetService)(
    implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  def newForm(entityId: Long) = Action { implicit request =>
    Ok(views.html.assetForm(AssetFormData.form, routes.AssetController.create(entityId), entityId, request.messages("drp.asset.new")))
  }

  def create(entityId: Long) = Action.async { implicit request =>
    AssetFormData.form.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(views.html.assetForm(formWithErrors, routes.AssetController.create(entityId), entityId, request.messages("drp.asset.new")))),
      data =>
        service.create(EntityId(entityId), data.assetType, data.value, data.metadata).map {
          case Right(_) =>
            Redirect(routes.EntityController.view(entityId)).flashing("success" -> request.messages("drp.asset.created"))
          case Left(err) =>
            BadRequest(views.html.assetForm(AssetFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.AssetController.create(entityId), entityId, request.messages("drp.asset.new")))
        }
    )
  }

  def editForm(id: Long) = Action.async { implicit request =>
    service.get(AssetId(id)).map {
      case Some(a) =>
        Ok(views.html.assetForm(AssetFormData.form.fill(AssetFormData.from(a)), routes.AssetController.update(id), a.entityId.value, request.messages("drp.asset.edit")))
      case None => NotFound(request.messages("drp.asset.notFound"))
    }
  }

  def update(id: Long) = Action.async { implicit request =>
    service.get(AssetId(id)).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.asset.notFound")))
      case Some(existing) =>
        AssetFormData.form.bindFromRequest().fold(
          formWithErrors =>
            Future.successful(BadRequest(views.html.assetForm(formWithErrors, routes.AssetController.update(id), existing.entityId.value, request.messages("drp.asset.edit")))),
          data =>
            service.update(AssetId(id), data.assetType, data.value, data.metadata).map {
              case Right(a) =>
                Redirect(routes.EntityController.view(a.entityId.value)).flashing("success" -> request.messages("drp.asset.updated"))
              case Left(err) =>
                BadRequest(views.html.assetForm(AssetFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.AssetController.update(id), existing.entityId.value, request.messages("drp.asset.edit")))
            }
        )
    }
  }
}
