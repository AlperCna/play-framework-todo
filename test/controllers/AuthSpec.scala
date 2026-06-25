package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

class AuthSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "Protected pages" should {
    "redirect to /todo/login when not authenticated" in {
      val result = route(app, FakeRequest(GET, "/todo/tasks")).get
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/todo/login")
    }
  }

  "Login page" should {
    "be publicly accessible" in {
      val result = route(app, FakeRequest(GET, "/todo/login")).get
      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
    }
  }
}
