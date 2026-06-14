package controllers

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import org.pac4j.core.config.Config
import org.pac4j.http.client.indirect.FormClient
import play.api.mvc._

import forms.RegisterFormData
import services.UserService

/**
 * Kimlik dogrulama ucu — PUBLIC.
 *
 * Giris ve cikis artik pac4j'ye devredildi:
 *   - giris: `/login` formu pac4j FormClient'in callback URL'ine POST eder; kimlik
 *     dogrulama `CallbackController` + [[security.DbUsernamePasswordAuthenticator]]
 *     tarafindan yapilir (bkz. routes `/callback`).
 *   - cikis: pac4j `LogoutController` (`GET /logout`).
 *
 * Bu controller'da yalnizca login FORMUNU gostermek ve KAYIT (register) akisi kalir.
 */
@Singleton
class AuthController @Inject() (
    userService: UserService,
    config: Config,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  /** GIRIS formu: pac4j FormClient'in callback URL'ine POST eder; `?error` varsa gosterilir. */
  def loginForm(): Action[AnyContent] = Action { implicit request =>
    val formClient = config.getClients.findClient("FormClient").get.asInstanceOf[FormClient]
    Ok(views.html.auth.login(formClient.getCallbackUrl, request.getQueryString("error")))
  }

  /** KAYIT formu. */
  def registerForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.auth.register(RegisterFormData.form))
  }

  /**
   * KAYIT (kaydet): kullaniciyi DB'de olusturur, sonra `/login`'e yonlendirir
   * (giris pac4j uzerinden yapilir).
   */
  def register(): Action[AnyContent] = Action.async { implicit request =>
    RegisterFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.register(formWithErrors))),
      data =>
        userService.register(data.email, data.password).map {
          case Right(_) =>
            Redirect(routes.AuthController.loginForm()).flashing("success" -> request.messages("auth.registered"))
          case Left(err) =>
            BadRequest(views.html.auth.register(RegisterFormData.form.fill(data).withGlobalError(request.messages(err.code))))
        }
    )
  }
}
