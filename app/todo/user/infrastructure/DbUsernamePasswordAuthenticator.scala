package todo.user.infrastructure

import java.util.Optional
import javax.inject.{Inject, Singleton}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import org.pac4j.core.context.CallContext
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.credentials.{Credentials, UsernamePasswordCredentials}
import org.pac4j.core.exception.CredentialsException
import org.pac4j.core.profile.CommonProfile

import todo.user.application.UserService

@Singleton
class DbUsernamePasswordAuthenticator @Inject() (userService: UserService)(
    implicit ec: ExecutionContext
) extends Authenticator {

  override def validate(ctx: CallContext, credentials: Credentials): Optional[Credentials] = {
    val upc = credentials.asInstanceOf[UsernamePasswordCredentials]
    Await.result(userService.login(upc.getUsername, upc.getPassword), 5.seconds) match {
      case Right(user) =>
        val profile = new CommonProfile()
        profile.setId(user.id.toString)
        profile.addAttribute("email", user.email)
        credentials.setUserProfile(profile)
        Optional.of(credentials)
      case Left(_) =>
        throw new CredentialsException("Invalid credentials")
    }
  }
}
