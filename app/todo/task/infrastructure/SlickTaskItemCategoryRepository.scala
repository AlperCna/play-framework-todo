package todo.task.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider

import todo.shared.infrastructure.RowMapper
import todo.shared.infrastructure.SlickCrudSupport
import todo.task.application.TaskItemCategoryRepository
import todo.task.domain.TaskItemCategory
import todo.task.infrastructure.TaskMappers._

@Singleton
class SlickTaskItemCategoryRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends TaskItemCategoryRepository
    with SlickCrudSupport {

  import profile.api._

  private val mapper = RowMapper[TaskItemCategory, TaskItemCategoryRow]

  override def listByTask(taskItemId: Long): Future[Seq[TaskItemCategory]] =
    listActive(taskItemCategories.filter(_.taskItemId === taskItemId))

  override def findActiveLink(taskItemId: Long, categoryId: Long): Future[Option[TaskItemCategory]] =
    findOneActive(taskItemCategories.filter(l => l.taskItemId === taskItemId && l.categoryId === categoryId))

  override def add(link: TaskItemCategory): Future[TaskItemCategory] =
    insertReturningId(taskItemCategories, mapper.toRow(link)).map(newId => link.copy(id = newId))

  override def update(link: TaskItemCategory): Future[Option[TaskItemCategory]] = {
    val row = mapper.toRow(link)
    db.run(
      taskItemCategories
        .filter(_.id === link.id)
        .map(l =>
          (l.taskItemId, l.categoryId, l.createdAt, l.createdBy, l.updatedAt, l.updatedBy, l.isDeleted, l.deletedAt, l.deletedBy)
        )
        .update(
          (row.taskItemId, row.categoryId, row.createdAt, row.createdBy, row.updatedAt, row.updatedBy, row.isDeleted, row.deletedAt, row.deletedBy)
        )
    ).map(n => if (n > 0) Some(link) else None)
  }
}
