package todo.task.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

import todo.shared.infrastructure.Database
import todo.task.application.TaskItemCategoryRepository
import todo.task.domain.TaskItemCategory

@Singleton
class InMemoryTaskItemCategoryRepository @Inject() (db: Database)
    extends TaskItemCategoryRepository {

  override def listByTask(taskItemId: Long): Future[Seq[TaskItemCategory]] =
    Future.successful(db.taskCategories.find(_.taskItemId == taskItemId))

  override def findActiveLink(taskItemId: Long, categoryId: Long): Future[Option[TaskItemCategory]] =
    Future.successful(
      db.taskCategories
        .find(l => l.taskItemId == taskItemId && l.categoryId == categoryId)
        .headOption
    )

  override def add(link: TaskItemCategory): Future[TaskItemCategory] =
    Future.successful(db.taskCategories.add(link))

  override def update(link: TaskItemCategory): Future[Option[TaskItemCategory]] =
    Future.successful(db.taskCategories.findById(link.id, includeDeleted = true).map { _ =>
      db.taskCategories.put(link)
      link
    })
}
