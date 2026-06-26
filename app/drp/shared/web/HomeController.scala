package drp.shared.web

import javax.inject.{Inject, Singleton}

import play.api.mvc._

@Singleton
class HomeController @Inject() (cc: ControllerComponents) extends AbstractController(cc) {

  def index(): Action[AnyContent] = Action {
    Ok(drp.shared.web.views.html.index())
  }

}
