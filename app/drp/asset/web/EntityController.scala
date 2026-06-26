package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import drp.asset.application.EntityService

/** Registers protected entities (POST → redirect to the listing). No auth in this slice. */
@Singleton
class EntityController @Inject() (
    service: EntityService,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def create(): Action[AnyContent] = Action.async { implicit request =>
    EntityFormData.form
      .bindFromRequest()
      .fold(
        _ =>
          Future.successful(
            Redirect(routes.AssetController.list())
              .flashing("error" -> "Entity name and type are required.")
          ),
        data =>
          service.register(data.name, data.entityType).map {
            case Right(entity) =>
              Redirect(routes.AssetController.list())
                .flashing("success" -> s"Entity '${entity.name}' registered.")
            case Left(error) =>
              Redirect(routes.AssetController.list()).flashing("error" -> error.message)
          }
      )
  }
}
