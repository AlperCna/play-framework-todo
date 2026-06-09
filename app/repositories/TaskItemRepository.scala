package repositories

import domain.task.TaskItem

/**
 * TaskItem verilerine erisim sozlesmesi (port).
 *
 * Saf veri erisimidir: is kurali tasimaz; gecerli bir entity'yi (servis katmani
 * smart constructor ile uretir) persist eder. Sorgular varsayilan olarak
 * soft-delete edilmemis kayitlari doner (R9).
 *
 * Metotlar senkron; ogrenmeyi sade tutmak icin. Gercek bir DB'de muhtemelen
 * `Future[...]` donerlerdi.
 */
trait TaskItemRepository {

  /** Silinmemis tum gorevler. */
  def list(): Seq[TaskItem]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Option[TaskItem]

  /** Yeni gorev ekler; atanmis id ile kaydedilmis hali doner. */
  def add(task: TaskItem): TaskItem

  /** Var olan gorevi (tum entity'yi) gunceller; kayit yoksa None. */
  def update(task: TaskItem): Option[TaskItem]

  /** Bir kullanicinin silinmemis gorevleri. */
  def listByUser(userId: Long): Seq[TaskItem]
}
