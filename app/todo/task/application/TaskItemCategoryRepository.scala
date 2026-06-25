package todo.task.application

import scala.concurrent.Future

import todo.task.domain.TaskItemCategory

trait TaskItemCategoryRepository {

  def listByTask(taskItemId: Long): Future[Seq[TaskItemCategory]]

  def findActiveLink(taskItemId: Long, categoryId: Long): Future[Option[TaskItemCategory]]

  def add(link: TaskItemCategory): Future[TaskItemCategory]

  def update(link: TaskItemCategory): Future[Option[TaskItemCategory]]
}
