package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

/**
 * Auth entegrasyon testi: korunan sayfalar (AuthenticatedAction) giris yoksa
 * /login'e yonlendirir; gecerli session ile erisilebilir.
 *
 * Seed kullanicinin id'si 1 (InMemoryDatabase ilk demo user'i ekler).
 */
class AuthSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "Protected pages" should {

    "redirect to /login when not authenticated" in {
      val result = route(app, FakeRequest(GET, "/tasks")).get
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/login")
    }

    "allow access with a valid session" in {
      val result = route(app, FakeRequest(GET, "/tasks").withSession("userId" -> "1")).get
      status(result) mustBe OK
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
