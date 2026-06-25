package repositories.interfaces

import scala.concurrent.Future

import domain.category.Category
import pagination.{Page, PageRequest}

/**
 * Category verilerine erisim sozlesmesi (port).
 *
 * Standart CRUD [[CrudRepository]]'den; burada yalnizca Category'ye ozgu sorgu.
 * Sorgular varsayilan olarak silinmemisleri doner.
 */
trait CategoryRepository extends CrudRepository[Category] {

  /**
   * Bir kullanicinin silinmemis kategorileri (TUM liste).
   * Gorev-atama dropdown'i gibi tam listeye ihtiyac duyan yerler bunu kullanir.
   */
  def listByUser(userId: Long): Future[Seq[Category]]

  /** Bir kullanicinin silinmemis kategorileri, SAYFALANMIS (liste sayfasi icin). */
  def listByUser(userId: Long, page: PageRequest): Future[Page[Category]]
}
