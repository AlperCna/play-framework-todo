package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import drp.asset.application.ExclusionService
import drp.asset.domain.{EntityId, ExclusionId}

/** Thin web layer for exclusions (entity-scoped create; id-scoped edit). No delete. */
@Singleton
class ExclusionController @Inject() (cc: MessagesControllerComponents, service: ExclusionService)(
    implicit ec: ExecutionContext
) extends MessagesAbstractController(cc) {

  def newForm(entityId: Long) = Action { implicit request =>
    Ok(views.html.exclusionForm(ExclusionFormData.form, routes.ExclusionController.create(entityId), entityId, request.messages("drp.exclusion.new")))
  }

  def create(entityId: Long) = Action.async { implicit request =>
    ExclusionFormData.form.bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(views.html.exclusionForm(formWithErrors, routes.ExclusionController.create(entityId), entityId, request.messages("drp.exclusion.new")))),
      data =>
        service.create(EntityId(entityId), data.value, data.matchType, data.reason).map {
          case Right(_) =>
            Redirect(routes.EntityController.view(entityId)).flashing("success" -> request.messages("drp.exclusion.created"))
          case Left(err) =>
            BadRequest(views.html.exclusionForm(ExclusionFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.ExclusionController.create(entityId), entityId, request.messages("drp.exclusion.new")))
        }
    )
  }

  def editForm(id: Long) = Action.async { implicit request =>
    service.get(ExclusionId(id)).map {
      case Some(x) =>
        Ok(views.html.exclusionForm(ExclusionFormData.form.fill(ExclusionFormData.from(x)), routes.ExclusionController.update(id), x.entityId.value, request.messages("drp.exclusion.edit")))
      case None => NotFound(request.messages("drp.exclusion.notFound"))
    }
  }

  def update(id: Long) = Action.async { implicit request =>
    service.get(ExclusionId(id)).flatMap {
      case None => Future.successful(NotFound(request.messages("drp.exclusion.notFound")))
      case Some(existing) =>
        ExclusionFormData.form.bindFromRequest().fold(
          formWithErrors =>
            Future.successful(BadRequest(views.html.exclusionForm(formWithErrors, routes.ExclusionController.update(id), existing.entityId.value, request.messages("drp.exclusion.edit")))),
          data =>
            service.update(ExclusionId(id), data.value, data.matchType, data.reason).map {
              case Right(x) =>
                Redirect(routes.EntityController.view(x.entityId.value)).flashing("success" -> request.messages("drp.exclusion.updated"))
              case Left(err) =>
                BadRequest(views.html.exclusionForm(ExclusionFormData.form.fill(data).withGlobalError(request.messages(err.code)), routes.ExclusionController.update(id), existing.entityId.value, request.messages("drp.exclusion.edit")))
            }
        )
    }
  }
}
