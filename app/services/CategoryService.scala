package services

import scala.concurrent.Future

import domain.category.Category
import domain.common.DomainError
import pagination.{Page, PageRequest}

/**
 * Category uygulama servisi.
 *
 * Gercek DB ile repo'lar `Future` dondugu icin metotlar `Future` doner.
 */
trait CategoryService {
  def list(): Future[Seq[Category]]

  /** Bir kullanicinin TUM kategorileri (orn. gorev-atama dropdown'i icin). */
  def listByUser(userId: Long): Future[Seq[Category]]

  /** Bir kullanicinin kategorileri, SAYFALANMIS (liste sayfasi icin). */
  def listByUser(userId: Long, page: PageRequest): Future[Page[Category]]

  def get(id: Long): Future[Option[Category]]
  def create(name: String, description: String, userId: Long): Future[Either[DomainError, Category]]

  /** Form-tabanli birlesik guncelleme: ad + aciklama ayni anda. */
  def update(id: Long, name: String, description: String): Future[Either[DomainError, Category]]

  def rename(id: Long, name: String): Future[Either[DomainError, Category]]
  def changeDescription(id: Long, description: String): Future[Either[DomainError, Category]]
  def delete(id: Long): Future[Either[DomainError, Category]]
}
