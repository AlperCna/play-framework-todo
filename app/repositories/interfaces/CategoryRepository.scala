package repositories.interfaces

import scala.concurrent.Future

import domain.category.Category

/**
 * Category verilerine erisim sozlesmesi (port).
 *
 * Standart CRUD [[CrudRepository]]'den; burada yalnizca Category'ye ozgu sorgu.
 * Sorgular varsayilan olarak silinmemisleri doner.
 */
trait CategoryRepository extends CrudRepository[Category] {

  /** Bir kullanicinin silinmemis kategorileri. */
  def listByUser(userId: Long): Future[Seq[Category]]
}
