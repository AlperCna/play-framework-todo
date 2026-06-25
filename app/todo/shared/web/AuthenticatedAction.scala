package todo.shared.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import org.pac4j.core.config.Config
import org.pac4j.play.context.PlayFrameworkParameters
import play.api.i18n.MessagesApi
import play.api.mvc._

import todo.user.application.UserRepository

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
    val toLogin = Results.Redirect(todo.user.web.routes.AuthController.loginForm())

    val parameters = new PlayFrameworkParameters(request)
    val webContext = config.getWebContextFactory.newContext(parameters)
    val sessionStore = config.getSessionStoreFactory.newSessionStore(parameters)
    val profileManager = config.getProfileManagerFactory.apply(webContext, sessionStore)
    val maybeProfile = profileManager.getProfile

    if (maybeProfile.isPresent) {
      userRepo.get(maybeProfile.get.getId.toLong).flatMap {
        case Some(user) => block(new AuthenticatedRequest(user, request, messagesApi))
        case None       => Future.successful(toLogin)
      }
    } else {
      Future.successful(toLogin)
    }
  }
}
