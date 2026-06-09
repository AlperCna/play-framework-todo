package controllers

import javax.inject._

import play.api.mvc._

import domain.common.DomainError
import forms.TaskItemFormData
import repositories.UserRepository
import services.TaskItemService

/**
 * Gorevler (TaskItem) uzerinde CRUD + tamamla/yeniden-ac islemlerini yoneten
 * controller.
 *
 * INCE controller: repository'ye DEGIL [[TaskItemService]]'e baglidir. Isi
 * yalnizca form'u baglamak ve servisten donen `Either[DomainError, T]` sonucunu
 * HTTP'ye (form hatasi / flash / redirect / 404) cevirmektir.
 *
 * KATMAN AYRIMI (onemli ogretici nokta):
 *   - Form dogrulamasi  = SOZDIZIMSEL hata (bos baslik, bozuk tarih, gecersiz oncelik).
 *   - Domain (servis)   = IS KURALI (High oncelik dueDate ister, gecmis tarih
 *                         tamamlanamaz). Bu hatalar global form hatasi ya da
 *                         flash "error" olarak gosterilir.
 */
@Singleton
class TaskItemController @Inject() (
    service: TaskItemService,
    userRepo: UserRepository,
    cc: MessagesControllerComponents
) extends MessagesAbstractController(cc) {

  /**
   * Form tanimi artik [[forms.TaskItemFormData]]'nin companion object'inde
   * (`TaskItemFormData.form`). Controller HTTP/akis isiyle ilgilenir; girdi
   * mapping'i ve sozdizimsel dogrulama form modulunde durur.
   *
   * Create icin sahip kullanici. Gercek auth olmadigindan seed kullanicinin
   * id'sini kullaniyoruz (hardcode yerine; seed'i izler).
   */
  private def defaultUserId: Long =
    userRepo.list().headOption.map(_.id).getOrElse(1L)

  /** READ (liste). */
  def list(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.tasks.list(service.list()))
  }

  /** CREATE (form). */
  def createForm(): Action[AnyContent] = Action { implicit request =>
    // TaskItemFormData.form: bos (ici bos) gorev formu. EditForm'daki gibi `fill` yapilmiyor.
    Ok(views.html.tasks.form(TaskItemFormData.form, routes.TaskItemController.create(), request.messages("task.form.new")))
  }

  // 1) Action icine tanimladigimiz fonksiyonun parametresini implicit yapiyoruz, 
  //    boylece request parametresini derleyicinin kullanimina sunuyoruz. 
  // 2) implicit degerin kullanillacagi metodun parametresini de implicit yapiyoruz. 
  // 
  // --> Daha sonra derleyici calistiginda implicit beklenen alana bakip onun tipine gore implicit tanimlanmis 
  //      olan degeri otomatik sekilde parametre olarak geciyor (ayni tipte birden fazla implicit tanimi varsa derleyici ambiguity hatasi verir).
  //
  // Not: Bazi built-in Play metodlari (Ok, BadRequest, Redirect gibi) implicit request parametresi ister, 
  //      bu nedenle bizim tarafimizdan da implicit tanimlamasi yapilmasi gerekir. 

  /** CREATE (kaydet). */
  def create(): Action[AnyContent] = Action { implicit request =>
    val heading = request.messages("task.form.new")
    val postUrl = routes.TaskItemController.create()
    TaskItemFormData.form.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.tasks.form(formWithErrors, postUrl, heading)),
      data =>
        service.create(data.title, data.description, data.priority, data.dueDate, defaultUserId) match {
          case Right(_) =>
            Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages("task.created"))
          case Left(err) =>
            BadRequest(views.html.tasks.form(TaskItemFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  /** UPDATE (form). */
  def editForm(id: Long): Action[AnyContent] = Action { implicit request =>
    service.get(id) match {
      case Some(task) =>
        val filled = TaskItemFormData.form.fill(
          TaskItemFormData(task.title, task.description, task.priority, task.dueDate)
        )
        Ok(views.html.tasks.form(filled, routes.TaskItemController.update(id), request.messages("task.form.edit", id)))
      case None =>
        NotFound(request.messages("task.notFound", id))
    }
  }

  /** UPDATE (kaydet). */
  def update(id: Long): Action[AnyContent] = Action { implicit request =>
    val heading = request.messages("task.form.edit", id)
    val postUrl = routes.TaskItemController.update(id)
    TaskItemFormData.form.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.tasks.form(formWithErrors, postUrl, heading)),
      data =>
        service.update(id, data.title, data.description, data.priority, data.dueDate) match {
          case Right(_) =>
            Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages("task.updated"))
          case Left(DomainError.NotFound(_, _)) =>
            NotFound(request.messages("task.notFound", id))
          case Left(err) =>
            BadRequest(views.html.tasks.form(TaskItemFormData.form.fill(data).withGlobalError(request.messages(err.code)), postUrl, heading))
        }
    )
  }

  /** Gorevi tamamla. */
  def complete(id: Long): Action[AnyContent] = Action { implicit request =>
    redirectAfterAction(service.complete(id), id, "task.completed")
  }

  /** Gorevi yeniden ac. */
  def reopen(id: Long): Action[AnyContent] = Action { implicit request =>
    redirectAfterAction(service.reopen(id), id, "task.reopened")
  }

  /** Gorevi (soft) sil. */
  def delete(id: Long): Action[AnyContent] = Action { implicit request =>
    redirectAfterAction(service.delete(id), id, "task.deleted")
  }

  /**
   * Liste sayfasi action'lari (complete/reopen/delete) icin ortak akis:
   * basari -> flash success; NotFound -> 404; diger domain hatasi -> flash error.
   */
  private def redirectAfterAction(
      result: Either[DomainError, _],
      id: Long,
      successKey: String
  )(implicit request: MessagesRequest[AnyContent]): Result =
    result match {
      case Right(_) =>
        Redirect(routes.TaskItemController.list()).flashing("success" -> request.messages(successKey))
      case Left(DomainError.NotFound(_, _)) =>
        NotFound(request.messages("task.notFound", id))
      case Left(err) =>
        Redirect(routes.TaskItemController.list()).flashing("error" -> request.messages(err.code))
    }
}
