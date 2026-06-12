package actions

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import play.api.i18n.MessagesApi
import play.api.mvc._

import controllers.routes
import repositories.interfaces.UserRepository

/**
 * "Giris zorunlu" + "current user hazir" islerini tek yerde toplayan ozel
 * ActionBuilder. .NET'teki `[Authorize]` + `CurrentUser` ikilisinin Play
 * karsiligi.
 *
 * Her istekte session'daki `userId`'yi okur, kullaniciyi yukler ve action'a
 * [[AuthenticatedRequest]] olarak verir. Giris yoksa (ya da kullanici artik
 * yoksa) session'i temizleyip `/login`'e yonlendirir.
 *
 * Concrete `@Singleton` sinif oldugundan Guice dogrudan enjekte eder; ayrica
 * binding gerekmez. Controller'lar `Action` yerine bu builder'i kullanir:
 *   `def list() = authAction { implicit request => ... request.user ... }`
 */
@Singleton
class AuthenticatedAction @Inject() (
    userRepo: UserRepository,
    messagesApi: MessagesApi,
    val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {
    val loginRedirect = Results.Redirect(routes.AuthController.loginForm()).withNewSession
    request.session.get("userId").flatMap(s => Try(s.toLong).toOption) match {
      case Some(userId) =>
        // userRepo.get artik Future doner; kullanici varsa action'a gec, yoksa /login.
        userRepo.get(userId).flatMap {
          case Some(user) => block(new AuthenticatedRequest(user, request, messagesApi))
          case None       => Future.successful(loginRedirect)
        }
      case None =>
        Future.successful(loginRedirect)
    }
  }
}
