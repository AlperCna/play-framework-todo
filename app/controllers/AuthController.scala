package controllers

import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

import play.api.mvc._

import domain.user.User
import forms.{LoginFormData, RegisterFormData}
import services.UserService

/**
 * Kimlik dogrulama: kayit (register), giris (login), cikis (logout).
 *
 * Bu uclar PUBLIC'tir (login gerektirmez) — bu yuzden `AuthenticatedAction`
 * degil, sade `Action` kullanir. `userService` artik `Future` dondugu icin
 * login/register `Action.async` ile yazilir.
 */
@Singleton
class AuthController @Inject() (
    userService: UserService,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends MessagesAbstractController(cc) {

  /** Basarili giris/kayit sonrasi kullaniciyi session'a yazip /tasks'a yonlendirir. */
  private def signIn(user: User, flashKey: String)(implicit request: MessagesRequest[AnyContent]): Result =
    Redirect(routes.TaskItemController.list())
      .withSession("userId" -> user.id.toString, "userEmail" -> user.email)
      .flashing("success" -> request.messages(flashKey))

  /** GIRIS formu. */
  def loginForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.auth.login(LoginFormData.form))
  }

  /** GIRIS (kaydet). */
  def login(): Action[AnyContent] = Action.async { implicit request =>
    LoginFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.login(formWithErrors))),
      data =>
        userService.login(data.email, data.password).map {
          case Right(user) => signIn(user, "auth.loggedIn")
          case Left(err) =>
            BadRequest(views.html.auth.login(LoginFormData.form.fill(data).withGlobalError(request.messages(err.code))))
        }
    )
  }

  /** KAYIT formu. */
  def registerForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.auth.register(RegisterFormData.form))
  }

  /** KAYIT (kaydet) — basariliysa otomatik giris. */
  def register(): Action[AnyContent] = Action.async { implicit request =>
    RegisterFormData.form.bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest(views.html.auth.register(formWithErrors))),
      data =>
        userService.register(data.email, data.password).map {
          case Right(user) => signIn(user, "auth.registered")
          case Left(err) =>
            BadRequest(views.html.auth.register(RegisterFormData.form.fill(data).withGlobalError(request.messages(err.code))))
        }
    )
  }

  /** CIKIS — session temizlenir. */
  def logout(): Action[AnyContent] = Action { implicit request =>
    Redirect(routes.AuthController.loginForm())
      .withNewSession
      .flashing("success" -> request.messages("auth.loggedOut"))
  }
}
