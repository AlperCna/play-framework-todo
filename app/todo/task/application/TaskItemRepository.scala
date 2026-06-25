package todo.task.application

import scala.concurrent.Future

import todo.shared.application.CrudRepository
import todo.shared.application.Page
import todo.shared.application.PageRequest
import todo.task.domain.TaskItem

trait TaskItemRepository extends CrudRepository[TaskItem] {

  def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]]

  def hasCompletedByUser(userId: Long): Future[Boolean]
}
