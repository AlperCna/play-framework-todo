package services

import domain.category.Category
import domain.common.DomainError

/**
 * Category uygulama servisi.
 *
 * Bu fazda bir controller'i yok; domain+repo katmaniyla ayni simetride yazilir
 * ve sonraki faz (Category UI) icin hazirdir.
 */
trait CategoryService {
  def list(): Seq[Category]
  def listByUser(userId: Long): Seq[Category]
  def get(id: Long): Option[Category]
  def create(name: String, description: String, userId: Long): Either[DomainError, Category]

  /** Form-tabanli birlesik guncelleme: ad + aciklama ayni anda. */
  def update(id: Long, name: String, description: String): Either[DomainError, Category]

  def rename(id: Long, name: String): Either[DomainError, Category]
  def changeDescription(id: Long, description: String): Either[DomainError, Category]
  def delete(id: Long): Either[DomainError, Category]
}
