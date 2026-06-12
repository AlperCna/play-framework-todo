package repositories.interfaces

import scala.concurrent.Future

import domain.task.TaskItem

/**
 * TaskItem verilerine erisim sozlesmesi (port).
 *
 * Saf veri erisimidir: is kurali tasimaz; gecerli bir entity'yi (servis katmani
 * smart constructor ile uretir) persist eder. Sorgular varsayilan olarak
 * soft-delete edilmemis kayitlari doner (R9).
 *
 * Gercek (Slick) DB'ye gectigimiz icin metotlar artik `Future` doner; bellek-ici
 * implementasyon da bu imzaya `Future.successful` ile uyar.
 */
trait TaskItemRepository {

  /** Silinmemis tum gorevler. */
  def list(): Future[Seq[TaskItem]]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Future[Option[TaskItem]]

  /** Yeni gorev ekler; atanmis id ile kaydedilmis hali doner. */
  def add(task: TaskItem): Future[TaskItem]

  /** Var olan gorevi (tum entity'yi) gunceller; kayit yoksa None. */
  def update(task: TaskItem): Future[Option[TaskItem]]

  /** Bir kullanicinin silinmemis gorevleri. */
  def listByUser(userId: Long): Future[Seq[TaskItem]]
}
