package repositories.interfaces

import scala.concurrent.Future

import domain.task.TaskItem
import pagination.{Page, PageRequest}

/**
 * TaskItem verilerine erisim sozlesmesi (port).
 *
 * Standart CRUD [[CrudRepository]]'den gelir; burada yalnizca TaskItem'a OZGU
 * sorgular bildirilir. Saf veri erisimidir (is kurali tasimaz); gecerli bir
 * entity'yi (servis smart constructor ile uretir) persist eder. Sorgular
 * varsayilan olarak soft-delete edilmemis kayitlari doner (R9).
 */
trait TaskItemRepository extends CrudRepository[TaskItem] {

  /** Bir kullanicinin silinmemis gorevleri, SAYFALANMIS. */
  def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]]

  /**
   * Kullanicinin (silinmemis) tamamlanmis gorevi var mi? "Tamamlananlari temizle"
   * butonunun gorunurlugu icin — SAYFALAMADAN BAGIMSIZ. Tum kayitlari cekmemek
   * icin sade bir EXISTS/varlik kontrolu yapar.
   */
  def hasCompletedByUser(userId: Long): Future[Boolean]
}
