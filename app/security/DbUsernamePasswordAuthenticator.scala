package security

import java.util.Optional
import javax.inject.{Inject, Singleton}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import org.pac4j.core.context.CallContext
import org.pac4j.core.credentials.authenticator.Authenticator
import org.pac4j.core.credentials.{Credentials, UsernamePasswordCredentials}
import org.pac4j.core.exception.CredentialsException
import org.pac4j.core.profile.CommonProfile

import services.UserService

/**
 * pac4j'nin FormClient'i icin OZEL Authenticator.
 *
 * Kimlik bilgisini (kullanici adi = email, parola) mevcut [[UserService.login]]
 * ile dogrular (plaintext kural aynen korunur). Basariliysa bir `CommonProfile`
 * uretir (id = user.id, email attribute'u) ve bunu credentials'a TAKAR; pac4j bu
 * profili oturum (cookie session store) icine yazar.
 *
 * NOT: pac4j Authenticator'lari SENKRONDUR; servisimiz ise `Future` doner. Bu
 * yuzden burada `Await.result` ile koprulenir (callback istek thread'inde, kisa
 * bir bloklama). Ogrenme projesi icin kabul edilebilir; gercek yuksek-trafik bir
 * uygulamada senkron bir kullanici sorgusu tercih edilirdi.
 */
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
        // FormClient bunu yakalar ve /login?error=... ile geri yonlendirir.
        throw new CredentialsException("Invalid credentials")
    }
  }
}
