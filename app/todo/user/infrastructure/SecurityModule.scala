package todo.user.infrastructure

import java.nio.charset.StandardCharsets
import javax.inject.Singleton

import com.google.inject.{AbstractModule, Provides}
import org.pac4j.core.client.Clients
import org.pac4j.core.client.direct.AnonymousClient
import org.pac4j.core.config.Config
import org.pac4j.core.context.FrameworkParameters
import org.pac4j.core.context.session.{SessionStore, SessionStoreFactory}
import org.pac4j.core.profile.factory.ProfileManagerFactory
import org.pac4j.http.client.indirect.FormClient
import org.pac4j.play.context.PlayContextFactory
import org.pac4j.play.scala.{DefaultSecurityComponents, SecurityComponents}
import org.pac4j.play.store.{PlayCookieSessionStore, ShiroAesDataEncrypter}
import org.pac4j.play.{CallbackController, LogoutController}
import play.api.{Configuration, Environment}

class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  private val baseUrl = configuration.get[String]("baseUrl")

  override def configure(): Unit = {
    val sKey = configuration.get[String]("play.http.secret.key").substring(0, 16)
    val dataEncrypter = new ShiroAesDataEncrypter(sKey.getBytes(StandardCharsets.UTF_8))
    val playSessionStore = new PlayCookieSessionStore(dataEncrypter)
    bind(classOf[SessionStore]).toInstance(playSessionStore)

    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])

    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/todo/tasks")
    bind(classOf[CallbackController]).toInstance(callbackController)

    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/todo/login")
    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  @Provides @Singleton
  def provideFormClient(authenticator: DbUsernamePasswordAuthenticator): FormClient =
    new FormClient(baseUrl + "/todo/login", authenticator)

  @Provides @Singleton
  def provideConfig(formClient: FormClient, sessionStore: SessionStore): Config = {
    val clients = new Clients(baseUrl + "/todo/callback", formClient, new AnonymousClient())
    val config = new Config(clients)
    config.setSessionStoreFactory(new SessionStoreFactory {
      override def newSessionStore(parameters: FrameworkParameters): SessionStore = sessionStore
    })
    config.setWebContextFactory(PlayContextFactory.INSTANCE)
    config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT)
    config
  }
}
