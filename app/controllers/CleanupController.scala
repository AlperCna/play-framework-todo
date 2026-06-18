package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import actors.{CompletedTaskCleaner, CompletedTaskCleanerScheduler}

/**
 * Ogrenme/gelistirme amacli, tamamlanmis gorev temizligini ELLE tetikleyen bakim ucu.
 * Gorev listesi sayfasindaki "Tamamlananlari Temizle" butonu buraya POST eder.
 * (Bir rol/yetki kavrami YOK; isim yalnizca tetikledigi isi anlatir.)
 *
 * NOT: gercek bir uygulamada boyle bir uc yetkiyle korunur (orn. AuthenticatedAction);
 * burada ogrenme kolayligi icin korumasiz birakildi.
 */
@Singleton
class CleanupController @Inject() (
    cc: MessagesControllerComponents,
    cleaner: CompletedTaskCleanerScheduler
) extends MessagesAbstractController(cc) {

  /**
   * Tamamlanmis gorev temizligini gece 01:00'i beklemeden HEMEN tetikler ve liste
   * sayfasina geri doner.
   *
   * Actor'e `RunNow` mesajini `tell` (`!`) ile yollar: FIRE-AND-FORGET — istek aninda
   * doner, gercek soft-delete actor'un mesaj dongusunde ASYNC gerceklesir. Bu yuzden
   * flash "tetiklendi" der; liste hemen yenilense de silinenler bir an sonra dusebilir
   * (gerekirse sayfayi tekrar yenile). Sonuc ayrica konsol log'unda da gorunur.
   */
  def runCleanup = Action { implicit request =>
    cleaner.ref ! CompletedTaskCleaner.RunNow
    Redirect(routes.TaskItemController.list())
      .flashing("success" -> request.messages("task.cleanup.triggered"))
  }
}
