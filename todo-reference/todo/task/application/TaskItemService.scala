package todo.task.application

import java.time.LocalDate

import scala.concurrent.Future

import todo.category.domain.Category
import todo.shared.domain.DomainError
import todo.shared.application.Page
import todo.shared.application.PageRequest
import todo.shared.domain.Priority
import todo.task.domain.{TaskItem, TaskItemCategory}

trait TaskItemService {

  def list(): Future[Seq[TaskItem]]

  def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]]

  def hasCompletedByUser(userId: Long): Future[Boolean]

  def get(id: Long): Future[Option[TaskItem]]

  def create(
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate],
      userId: Long
  ): Future[Either[DomainError, TaskItem]]

  def update(
      id: Long,
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate]
  ): Future[Either[DomainError, TaskItem]]

  def complete(id: Long): Future[Either[DomainError, TaskItem]]

  def reopen(id: Long): Future[Either[DomainError, TaskItem]]

  def delete(id: Long): Future[Either[DomainError, TaskItem]]

  def purgeCompleted(): Future[Int]

  def assignToCategory(
      taskId: Long,
      categoryId: Long
  ): Future[Either[DomainError, Option[TaskItemCategory]]]

  def removeFromCategory(taskId: Long, categoryId: Long): Future[Either[DomainError, Unit]]

  def categoriesOf(taskId: Long): Future[Seq[Category]]
}
