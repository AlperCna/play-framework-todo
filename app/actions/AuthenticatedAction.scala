package actions

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import org.pac4j.core.config.Config
import org.pac4j.play.context.PlayFrameworkParameters
import play.api.i18n.MessagesApi
import play.api.mvc._

import controllers.routes
import repositories.interfaces.UserRepository

/**
 * "Giris zorunlu" + "current user hazir" islerini tek yerde toplayan ozel
 * ActionBuilder. .NET'teki `[Authorize]` + `CurrentUser` ikilisinin Play
 * karsiligi.
 *
 * Kimlik dogrulama artik pac4j tarafindan yapilir (FormClient + callback). Bu
 * builder her istekte pac4j PROFILINI okur:
 *   - profil varsa: id'sinden `User`'i yukler ve action'a [[AuthenticatedRequest]]
 *     olarak verir.
 *   - profil yoksa (ya da kullanici artik yoksa): `/login`'e yonlendirir.
 *
 * Boylece controller'lar ve view'lar DEGISMEDEN calismaya devam eder; sadece
 * "current user nereden geliyor" sorusunun cevabi session yerine pac4j profili
 * oldu.
 */
@Singleton
class AuthenticatedAction @Inject() (
    config: Config,
    userRepo: UserRepository,
    messagesApi: MessagesApi,
    val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  override def invokeBlock[A](
      request: Request[A],
      block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {
    val toLogin = Results.Redirect(routes.AuthController.loginForm())

    // pac4j profilini, istekten bir web-context + session store kurarak oku.
    val parameters = new PlayFrameworkParameters(request)
    val webContext = config.getWebContextFactory.newContext(parameters)
    val sessionStore = config.getSessionStoreFactory.newSessionStore(parameters)
    val profileManager = config.getProfileManagerFactory.apply(webContext, sessionStore)
    val maybeProfile = profileManager.getProfile

    if (maybeProfile.isPresent) {
      userRepo.get(maybeProfile.get.getId.toLong).flatMap {
        case Some(user) => block(new AuthenticatedRequest(user, request, messagesApi))
        case None       => Future.successful(toLogin) // profil var ama kullanici DB'de yok
      }
    } else {
      Future.successful(toLogin)
    }
  }
}
