package services

import java.time.LocalDate

import scala.concurrent.Future

import domain.category.Category
import domain.common.{DomainError, Priority}
import domain.task.{TaskItem, TaskItemCategory}
import pagination.{Page, PageRequest}

/**
 * TaskItem uygulama (application) servisi — orkestrasyon katmani.
 *
 * Controller bu arayuze baglanir (repository'ye DEGIL). Gorevi: repository'den
 * yukle -> domain davranisini cagir -> persist et -> sonuc don.
 * `now`/`today`/audit `by` gibi yan etkiler bu katmanda toplanir ve domain'e
 * parametre olarak gecirilir; boylece domain saf kalir.
 *
 * Gercek DB ile repo'lar `Future` dondugu icin servis de `Future` doner; hatali
 * is kurali sonuclari `Future[Either[DomainError, T]]` ile tasinir.
 */
trait TaskItemService {

  def list(): Future[Seq[TaskItem]]

  /**
   * Bir kullanicinin silinmemis gorevleri, SAYFALANMIS (CurrentUser'a gore liste).
   * Sayfalama bir is kurali degildir; repo'dan gelen [[pagination.Page]] aynen gecer.
   */
  def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]]

  /**
   * Kullanicinin temizlenebilecek (tamamlanmis, silinmemis) gorevi var mi?
   * Liste sayfasindaki "Tamamlananlari temizle" butonunun gorunurlugu icin;
   * pagination'dan BAGIMSIZ ([[purgeCompleted]] zaten TUM tamamlananlari temizler).
   */
  def hasCompletedByUser(userId: Long): Future[Boolean]

  def get(id: Long): Future[Option[TaskItem]]

  def create(
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate],
      userId: Long
  ): Future[Either[DomainError, TaskItem]]

  def update(
      id: Long,
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate]
  ): Future[Either[DomainError, TaskItem]]

  def complete(id: Long): Future[Either[DomainError, TaskItem]]

  def reopen(id: Long): Future[Either[DomainError, TaskItem]]

  def delete(id: Long): Future[Either[DomainError, TaskItem]]

  /**
   * Bakim islemi: tamamlanmis (ve henuz silinmemis) TUM gorevleri soft-delete eder;
   * silinen adedi doner. "NE YAPILACAK" mantigi burada toplanir; "NE ZAMAN" ise
   * zamanlanmis [[actors.CompletedTaskCleaner]] actor'unun isidir. Boylece is kurali
   * test edilebilir servis katmaninda kalir, actor ince bir zamanlayici olur.
   */
  def purgeCompleted(): Future[Int]

  /**
   * Gorevi bir kategoriye atar. Yeni iliski olustuysa `Some`, idempotent (zaten
   * atanmis) ise `None` doner. Silinmis kategoride `Left(CategoryDeleted)`.
   */
  def assignToCategory(
      taskId: Long,
      categoryId: Long
  ): Future[Either[DomainError, Option[TaskItemCategory]]]

  /**
   * Gorevi bir kategoriden cikarir (ilgili aktif iliskiyi soft-delete eder).
   * Iliski yoksa sessizce gecer (idempotent).
   */
  def removeFromCategory(taskId: Long, categoryId: Long): Future[Either[DomainError, Unit]]

  /** Gorevin atanmis (aktif, silinmemis) kategorileri. */
  def categoriesOf(taskId: Long): Future[Seq[Category]]
}
