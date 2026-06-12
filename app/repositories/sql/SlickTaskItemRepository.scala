package repositories.sql

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import domain.task.TaskItem
import persistence.db.{Mappers, Tables}
import repositories.interfaces.TaskItemRepository

/**
 * [[TaskItemRepository]]'nin Slick (SQL Server) implementasyonu.
 *
 * Domain'in `urgency` ADT'si [[Mappers]] tarafindan `priority_value` + `due_date`
 * kolonlarina duzlestirilir; okurken `Mappers.toTask` ile yeniden kurulur.
 */
@Singleton
class SlickTaskItemRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends TaskItemRepository
    with HasDatabaseConfigProvider[JdbcProfile]
    with Tables {

  import profile.api._

  override def list(): Future[Seq[TaskItem]] =
    db.run(tasks.filter(!_.isDeleted).sortBy(_.id).result).map(_.map(Mappers.toTask))

  override def get(id: Long): Future[Option[TaskItem]] =
    db.run(tasks.filter(t => t.id === id && !t.isDeleted).result.headOption).map(_.map(Mappers.toTask))

  override def listByUser(userId: Long): Future[Seq[TaskItem]] =
    db.run(tasks.filter(t => t.userId === userId && !t.isDeleted).sortBy(_.id).result)
      .map(_.map(Mappers.toTask))

  override def add(task: TaskItem): Future[TaskItem] = {
    val row = Mappers.toRow(task)
    db.run((tasks returning tasks.map(_.id)) += row).map(newId => task.copy(id = newId))
  }

  override def update(task: TaskItem): Future[Option[TaskItem]] = {
    val row = Mappers.toRow(task)
    db.run(
      tasks
        .filter(_.id === task.id)
        .map(t =>
          (t.title, t.description, t.priorityValue, t.dueDate, t.isCompleted, t.completedAt, t.userId,
           t.createdAt, t.createdBy, t.updatedAt, t.updatedBy, t.isDeleted, t.deletedAt, t.deletedBy)
        )
        .update(
          (row.title, row.description, row.priorityValue, row.dueDate, row.isCompleted, row.completedAt, row.userId,
           row.createdAt, row.createdBy, row.updatedAt, row.updatedBy, row.isDeleted, row.deletedAt, row.deletedBy)
        )
    ).map(n => if (n > 0) Some(task) else None)
  }
}
