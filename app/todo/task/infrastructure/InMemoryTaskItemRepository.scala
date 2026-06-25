package todo.task.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

import todo.shared.infrastructure.Database
import todo.shared.application.Page
import todo.shared.application.PageRequest
import todo.task.application.TaskItemRepository
import todo.task.domain.TaskItem

@Singleton
class InMemoryTaskItemRepository @Inject() (db: Database) extends TaskItemRepository {

  override def list(): Future[Seq[TaskItem]] = Future.successful(db.tasks.all())

  override def get(id: Long): Future[Option[TaskItem]] = Future.successful(db.tasks.findById(id))

  override def add(task: TaskItem): Future[TaskItem] = Future.successful(db.tasks.add(task))

  override def update(task: TaskItem): Future[Option[TaskItem]] =
    Future.successful(db.tasks.findById(task.id, includeDeleted = true).map { _ =>
      db.tasks.put(task)
      task
    })

  override def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]] = {
    val all    = db.tasks.find(_.userId.contains(userId))
    val window = all.slice(page.offset.toInt, page.offset.toInt + page.limit)
    Future.successful(Page.from(window, page, all.size.toLong))
  }

  override def hasCompletedByUser(userId: Long): Future[Boolean] =
    Future.successful(db.tasks.find(_.userId.contains(userId)).exists(_.isCompleted))
}
