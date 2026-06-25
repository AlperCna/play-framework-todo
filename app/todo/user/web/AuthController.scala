package todo.user.web

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import org.pac4j.core.config.Config
import org.pac4j.http.client.indirect.FormClient
import play.api.mvc._

import todo.user.application.UserService

@Singleton
class AuthController @Inject() (
    userService: UserService,
    config: Config,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  def loginForm(): Action[AnyContent] = Action { implicit request =>
    val formClient = config.getClients.findClient("FormClient").get.asInstanceOf[FormClient]
    Ok(todo.user.web.views.html.login(formClient.getCallbackUrl, request.getQueryString("error")))
  }

  def registerForm(): Action[AnyContent] = Action { implicit request =>
    Ok(todo.user.web.views.html.register(RegisterFormData.form))
  }

  def register(): Action[AnyContent] = Action.async { implicit request =>
    RegisterFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(todo.user.web.views.html.register(formWithErrors))),
      data =>
        userService.register(data.email, data.password).map {
          case Right(_) =>
            Redirect(routes.AuthController.loginForm()).flashing("success" -> request.messages("auth.registered"))
          case Left(err) =>
            BadRequest(todo.user.web.views.html.register(RegisterFormData.form.fill(data).withGlobalError(request.messages(err.code))))
        }
    )
  }
}
