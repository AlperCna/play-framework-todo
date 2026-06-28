package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import drp.asset.application.ExclusionService
import drp.asset.application.ports.EntityRepository

/**
 * Server-rendered, per-entity exclusions surface. GET lists one entity's exclusions and hosts the
 * create form; POST registers an exclusion. Reached by its entity-scoped URL; the US-001 `/drp/assets`
 * view is not modified. No auth in this slice.
 */
@Singleton
class ExclusionController @Inject() (
    exclusionService: ExclusionService,
    entityRepository: EntityRepository,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def list(entityId: Long): Action[AnyContent] = Action.async { implicit request =>
    entityRepository.existsById(entityId).flatMap {
      case false =>
        Future.successful(
          Redirect(drp.asset.web.routes.AssetController.list())
            .flashing("error" -> s"No entity exists with id $entityId.")
        )
      case true =>
        exclusionService
          .listByEntity(entityId)
          .map(exclusions => Ok(drp.asset.web.views.html.exclusions(entityId, exclusions)))
    }
  }

  def create(entityId: Long): Action[AnyContent] = Action.async { implicit request =>
    ExclusionFormData.form
      .bindFromRequest()
      .fold(
        _ =>
          Future.successful(
            Redirect(routes.ExclusionController.list(entityId))
              .flashing("error" -> "Exclusion value, match type, and reason are required.")
          ),
        data =>
          exclusionService.register(entityId, data.value, data.matchType, data.reason).map {
            case Right(exclusion) =>
              Redirect(routes.ExclusionController.list(entityId))
                .flashing("success" -> s"Exclusion '${exclusion.value}' registered.")
            case Left(error) =>
              Redirect(routes.ExclusionController.list(entityId)).flashing("error" -> error.message)
          }
      )
  }
}
