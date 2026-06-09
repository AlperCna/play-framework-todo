package repositories

import javax.inject.{Inject, Singleton}

import domain.task.TaskItemCategory
import persistence.Database

/**
 * [[TaskItemCategoryRepository]]'nin bellek-ici implementasyonu; [[Database]]'e
 * delege eder.
 */
@Singleton
class InMemoryTaskItemCategoryRepository @Inject() (db: Database)
    extends TaskItemCategoryRepository {

  override def listByTask(taskItemId: Long): Seq[TaskItemCategory] =
    db.taskCategories.find(_.taskItemId == taskItemId)

  override def findActiveLink(taskItemId: Long, categoryId: Long): Option[TaskItemCategory] =
    db.taskCategories
      .find(l => l.taskItemId == taskItemId && l.categoryId == categoryId)
      .headOption

  override def add(link: TaskItemCategory): TaskItemCategory = db.taskCategories.add(link)

  override def update(link: TaskItemCategory): Option[TaskItemCategory] =
    db.taskCategories.findById(link.id, includeDeleted = true).map { _ =>
      db.taskCategories.put(link)
      link
    }
}
