package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import drp.asset.application.{AssetGroupService, AssetService}
import drp.asset.domain.{AssetId, EntityId}

/** Thin web layer for assets (entity-scoped create; id-scoped edit). Group dropdown is populated per entity. No delete. */
@Singleton
class AssetController @Inject() (
    cc: MessagesControllerComponents,
    service: AssetService,
    groupService: AssetGroupService
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  private def groupsFor(entityId: EntityId): Future[Seq[AssetGroupViewModel]] =
    groupService.listByEntity(entityId).map(_.map(AssetGroupViewModel.from))

  def newForm(entityId: Long) = Action.async { implicit request =>
    groupsFor(EntityId(entityId)).map { groups =>
      Ok(views.html.assetForm(AssetFormData.form, routes.AssetController.create(entityId), entityId, groups, request.messages("drp.asset.new")))
    }
  }

  def create(entityId: Long) = Action.async { implicit request =>
    val eid = EntityId(entityId)
    AssetFormData.form.bindFromRequest().fold(
      formWithErrors =>
        groupsFor(eid).map(groups => BadRequest(views.html.assetForm(formWithErrors, routes.AssetController.create(entityId), entityId, groups, request.messages("drp.asset.new")))),
      data =>
        service.create(eid, data.assetType, data.value, data.metadata, data.groupId).flatMap {
          case Right(_) =>
            Future.successful(Redirect(routes.EntityController.view(entityId)).flashing("success" -> request.messages("drp.asset.created")))
          case Left(err) =>
            groupsFor(eid).map(groups => BadRequest(views.html.assetForm(AssetFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.AssetController.create(entityId), entityId, groups, request.messages("drp.asset.new"))))
        }
    )
  }

  def editForm(id: Long) = Action.async { implicit request =>
    service.get(AssetId(id)).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.asset.notFound")))
      case Some(a) =>
        groupsFor(a.entityId).map { groups =>
          Ok(views.html.assetForm(AssetFormData.form.fill(AssetFormData.from(a)), routes.AssetController.update(id), a.entityId.value, groups, request.messages("drp.asset.edit")))
        }
    }
  }

  def update(id: Long) = Action.async { implicit request =>
    service.get(AssetId(id)).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.asset.notFound")))
      case Some(existing) =>
        AssetFormData.form.bindFromRequest().fold(
          formWithErrors =>
            groupsFor(existing.entityId).map(groups => BadRequest(views.html.assetForm(formWithErrors, routes.AssetController.update(id), existing.entityId.value, groups, request.messages("drp.asset.edit")))),
          data =>
            service.update(AssetId(id), data.assetType, data.value, data.metadata, data.groupId).flatMap {
              case Right(a) =>
                Future.successful(Redirect(routes.EntityController.view(a.entityId.value)).flashing("success" -> request.messages("drp.asset.updated")))
              case Left(err) =>
                groupsFor(existing.entityId).map(groups => BadRequest(views.html.assetForm(AssetFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.AssetController.update(id), existing.entityId.value, groups, request.messages("drp.asset.edit"))))
            }
        )
    }
  }
}
