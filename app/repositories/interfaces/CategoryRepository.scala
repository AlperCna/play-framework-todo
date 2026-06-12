package repositories.interfaces

import scala.concurrent.Future

import domain.category.Category

/** Category verilerine erisim sozlesmesi (port). Sorgular varsayilan: silinmemisler. */
trait CategoryRepository {

  /** Silinmemis tum kategoriler. */
  def list(): Future[Seq[Category]]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Future[Option[Category]]

  /** Bir kullanicinin silinmemis kategorileri. */
  def listByUser(userId: Long): Future[Seq[Category]]

  /** Yeni kategori ekler; atanmis id ile doner. */
  def add(category: Category): Future[Category]

  /** Var olan kategoriyi gunceller; yoksa None. */
  def update(category: Category): Future[Option[Category]]
}
