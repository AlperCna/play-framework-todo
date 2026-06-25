package todo.category.application

import scala.concurrent.Future

import todo.category.domain.Category
import todo.shared.application.CrudRepository
import todo.shared.application.Page
import todo.shared.application.PageRequest

trait CategoryRepository extends CrudRepository[Category] {

  def listByUser(userId: Long): Future[Seq[Category]]

  def listByUser(userId: Long, page: PageRequest): Future[Page[Category]]
}
