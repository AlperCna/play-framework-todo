package repositories.inmemory

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

import domain.task.TaskItemCategory
import persistence.inmemory.Database
import repositories.interfaces.TaskItemCategoryRepository

/**
 * [[TaskItemCategoryRepository]]'nin bellek-ici implementasyonu;
 * [[persistence.inmemory.Database]]'e delege eder.
 */
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
