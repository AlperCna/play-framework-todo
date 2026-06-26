package drp.asset.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.mvc._

import drp.asset.application.EntityService

/** Server-rendered listing of protected entities (and, from US2, their assets). */
@Singleton
class AssetController @Inject() (
    entityService: EntityService,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def list(): Action[AnyContent] = Action.async { implicit request =>
    entityService.list().map(entities => Ok(drp.asset.web.views.html.list(entities)))
  }
}
