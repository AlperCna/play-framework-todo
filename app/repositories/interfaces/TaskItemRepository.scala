package repositories.interfaces

import scala.concurrent.Future

import domain.task.TaskItem

/**
 * TaskItem verilerine erisim sozlesmesi (port).
 *
 * Standart CRUD [[CrudRepository]]'den gelir; burada yalnizca TaskItem'a OZGU
 * sorgular bildirilir. Saf veri erisimidir (is kurali tasimaz); gecerli bir
 * entity'yi (servis smart constructor ile uretir) persist eder. Sorgular
 * varsayilan olarak soft-delete edilmemis kayitlari doner (R9).
 */
trait TaskItemRepository extends CrudRepository[TaskItem] {

  /** Bir kullanicinin silinmemis gorevleri. */
  def listByUser(userId: Long): Future[Seq[TaskItem]]
}
