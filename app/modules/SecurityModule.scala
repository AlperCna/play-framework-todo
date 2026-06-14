package modules

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

import security.DbUsernamePasswordAuthenticator

/**
 * pac4j guvenlik yapilandirmasi (Guice modulu; application.conf'ta
 * `play.modules.enabled += "modules.SecurityModule"` ile yuklenir).
 *
 * Tek bir Client tanimlariz: `FormClient` (kullanici adi/parola formu) + bizim
 * [[DbUsernamePasswordAuthenticator]]. `AnonymousClient` callback zincirinin
 * tamamlanmasi icin eklenir. Profil, sifrelenmis cookie session store'da tutulur.
 *
 * Kimlik AKISI: korunan sayfa -> /login (FormClient loginUrl) -> form callback'e
 * POST eder -> CallbackController authenticator'i calistirir -> profil oturuma
 * yazilir -> defaultUrl (/tasks)'a yonlenir.
 */
class SecurityModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  private val baseUrl = configuration.get[String]("baseUrl")

  override def configure(): Unit = {
    // Cookie session store, secret.key'in ilk 16 karakteriyle AES sifreler.
    val sKey = configuration.get[String]("play.http.secret.key").substring(0, 16)
    val dataEncrypter = new ShiroAesDataEncrypter(sKey.getBytes(StandardCharsets.UTF_8))
    val playSessionStore = new PlayCookieSessionStore(dataEncrypter)
    bind(classOf[SessionStore]).toInstance(playSessionStore)

    bind(classOf[SecurityComponents]).to(classOf[DefaultSecurityComponents])

    // Form callback sonrasi varsayilan hedef.
    val callbackController = new CallbackController()
    callbackController.setDefaultUrl("/tasks")
    bind(classOf[CallbackController]).toInstance(callbackController)

    // Logout sonrasi hedef.
    val logoutController = new LogoutController()
    logoutController.setDefaultUrl("/login")
    bind(classOf[LogoutController]).toInstance(logoutController)
  }

  @Provides @Singleton
  def provideFormClient(authenticator: DbUsernamePasswordAuthenticator): FormClient =
    new FormClient(baseUrl + "/login", authenticator)

  @Provides @Singleton
  def provideConfig(formClient: FormClient, sessionStore: SessionStore): Config = {
    val clients = new Clients(baseUrl + "/callback", formClient, new AnonymousClient())
    val config = new Config(clients)
    config.setSessionStoreFactory(new SessionStoreFactory {
      override def newSessionStore(parameters: FrameworkParameters): SessionStore = sessionStore
    })
    // Play-ozel web-context fabrikasi + varsayilan profil yoneticisi.
    // Normalde bunlar SecurityComponents kurulurken set edilir; hibrit yaklasimda
    // hicbir controller `Secure` kullanmadigi icin SecurityComponents olusturulmaz,
    // o yuzden burada ACIKCA set ediyoruz (yoksa AuthenticatedAction'da NPE).
    config.setWebContextFactory(PlayContextFactory.INSTANCE)
    config.setProfileManagerFactory(ProfileManagerFactory.DEFAULT)
    config
  }
}
