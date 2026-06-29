package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import drp.asset.application.AssetGroupService
import drp.asset.domain.{AssetGroupId, EntityId}

/** Thin web layer for asset groups (entity-scoped create; id-scoped edit). No delete. */
@Singleton
class AssetGroupController @Inject() (cc: MessagesControllerComponents, service: AssetGroupService)(
    implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  def newForm(entityId: Long) = Action { implicit request =>
    Ok(views.html.assetGroupForm(AssetGroupFormData.form, routes.AssetGroupController.create(entityId), entityId, request.messages("drp.assetGroup.new")))
  }

  def create(entityId: Long) = Action.async { implicit request =>
    AssetGroupFormData.form.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(views.html.assetGroupForm(formWithErrors, routes.AssetGroupController.create(entityId), entityId, request.messages("drp.assetGroup.new")))),
      data =>
        service.create(EntityId(entityId), data.name).map {
          case Right(_) =>
            Redirect(routes.EntityController.view(entityId)).flashing("success" -> request.messages("drp.assetGroup.created"))
          case Left(err) =>
            BadRequest(views.html.assetGroupForm(AssetGroupFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.AssetGroupController.create(entityId), entityId, request.messages("drp.assetGroup.new")))
        }
    )
  }

  def editForm(id: Long) = Action.async { implicit request =>
    service.get(AssetGroupId(id)).map {
      case Some(g) =>
        Ok(views.html.assetGroupForm(AssetGroupFormData.form.fill(AssetGroupFormData.from(g)), routes.AssetGroupController.update(id), g.entityId.value, request.messages("drp.assetGroup.edit")))
      case None => NotFound(request.messages("drp.assetGroup.notFound"))
    }
  }

  def update(id: Long) = Action.async { implicit request =>
    service.get(AssetGroupId(id)).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.assetGroup.notFound")))
      case Some(existing) =>
        AssetGroupFormData.form.bindFromRequest().fold(
          formWithErrors =>
            Future.successful(BadRequest(views.html.assetGroupForm(formWithErrors, routes.AssetGroupController.update(id), existing.entityId.value, request.messages("drp.assetGroup.edit")))),
          data =>
            service.update(AssetGroupId(id), data.name).map {
              case Right(g) =>
                Redirect(routes.EntityController.view(g.entityId.value)).flashing("success" -> request.messages("drp.assetGroup.updated"))
              case Left(err) =>
                BadRequest(views.html.assetGroupForm(AssetGroupFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.AssetGroupController.update(id), existing.entityId.value, request.messages("drp.assetGroup.edit")))
            }
        )
    }
  }
}
