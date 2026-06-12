package repositories.sql

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import domain.task.TaskItemCategory
import persistence.db.{Mappers, Tables}
import repositories.interfaces.TaskItemCategoryRepository

/** [[TaskItemCategoryRepository]]'nin Slick (SQL Server) implementasyonu. */
@Singleton
class SlickTaskItemCategoryRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends TaskItemCategoryRepository
    with HasDatabaseConfigProvider[JdbcProfile]
    with Tables {

  import profile.api._

  override def listByTask(taskItemId: Long): Future[Seq[TaskItemCategory]] =
    db.run(taskItemCategories.filter(l => l.taskItemId === taskItemId && !l.isDeleted).sortBy(_.id).result)
      .map(_.map(Mappers.toLink))

  override def findActiveLink(taskItemId: Long, categoryId: Long): Future[Option[TaskItemCategory]] =
    db.run(
      taskItemCategories
        .filter(l => l.taskItemId === taskItemId && l.categoryId === categoryId && !l.isDeleted)
        .result
        .headOption
    ).map(_.map(Mappers.toLink))

  override def add(link: TaskItemCategory): Future[TaskItemCategory] = {
    val row = Mappers.toRow(link)
    db.run((taskItemCategories returning taskItemCategories.map(_.id)) += row)
      .map(newId => link.copy(id = newId))
  }

  override def update(link: TaskItemCategory): Future[Option[TaskItemCategory]] = {
    val row = Mappers.toRow(link)
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
