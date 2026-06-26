package todo.category.application

import scala.concurrent.Future

import todo.category.domain.Category
import todo.shared.domain.DomainError
import todo.shared.application.Page
import todo.shared.application.PageRequest

trait CategoryService {
  def list(): Future[Seq[Category]]

  def listByUser(userId: Long): Future[Seq[Category]]

  def listByUser(userId: Long, page: PageRequest): Future[Page[Category]]

  def get(id: Long): Future[Option[Category]]
  def create(name: String, description: String, userId: Long): Future[Either[DomainError, Category]]

  def update(id: Long, name: String, description: String): Future[Either[DomainError, Category]]

  def rename(id: Long, name: String): Future[Either[DomainError, Category]]
  def changeDescription(id: Long, description: String): Future[Either[DomainError, Category]]
  def delete(id: Long): Future[Either[DomainError, Category]]
}
