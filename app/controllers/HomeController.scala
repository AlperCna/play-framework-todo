package controllers

import javax.inject._
import play.api.mvc._

/**
 * Uygulamanin ana sayfasini render eden controller.
 *
 * `MessagesAbstractController`'dan turuyoruz cunku ortak layout (`main`) artik
 * nav menusu icin ortulu bir `MessagesRequestHeader` istiyor; bu taban sinifin
 * `Action`'i istek olarak `MessagesRequest` saglar.
 */
@Singleton
class HomeController @Inject()(cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  /**
   * `routes` dosyasindaki yapilandirma geregi bu metot `GET /` istegi geldiginde
   * cagrilir.
   */
  def index() = Action { implicit request =>
    Ok(views.html.index())
  }
}
