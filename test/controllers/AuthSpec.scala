package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

/**
 * Auth entegrasyon testi (pac4j sonrasi).
 *
 * Korunan sayfalar `AuthenticatedAction` ile korunur; bu action artik pac4j
 * profilini okur. Profil yoksa `/login`'e yonlendirir.
 *
 * NOT: Eski "gecerli session ile eris" testi kaldirildi: giris durumu artik Play
 * session'indaki `userId` degil, pac4j'nin SIFRELENMIS cookie session store'undaki
 * profildir; bunu bir FakeRequest'te taklit etmek (gecerli pac4j cookie uretmek)
 * pratik degil. Giris akisi tarayicidan uctan uca dogrulanir (bkz. plan).
 */
class AuthSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "Protected pages" should {
    "redirect to /login when not authenticated" in {
      val result = route(app, FakeRequest(GET, "/tasks")).get
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/login")
    }
  }

  "Login page" should {
    "be publicly accessible" in {
      val result = route(app, FakeRequest(GET, "/login")).get
      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
    }
  }
}
