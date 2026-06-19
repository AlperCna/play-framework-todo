package repositories.sql

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider

import domain.task.TaskItem
import persistence.db.mappers.TaskMappers._ // RowMapper[TaskItem, TaskRow] implicit instance'i
import persistence.db.{RowMapper, TaskRow}
import repositories.interfaces.TaskItemRepository

/**
 * [[TaskItemRepository]]'nin Slick (SQL Server) implementasyonu.
 *
 * Ortak CRUD [[SlickCrudSupport]]'tan gelir. Domain'in `urgency` ADT'si `RowMapper`
 * instance'i (`TaskMappers`) tarafindan `priority_value` + `due_date`'e duzlestirilir;
 * okurken `toDomain` ile yeniden kurulur.
 */
@Singleton
class SlickTaskItemRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends TaskItemRepository
    with SlickCrudSupport {

  import profile.api._

  private val mapper = RowMapper[TaskItem, TaskRow]

  override def list(): Future[Seq[TaskItem]] = listActive(tasks)

  override def get(id: Long): Future[Option[TaskItem]] = findOneActive(tasks.filter(_.id === id))

  override def listByUser(userId: Long): Future[Seq[TaskItem]] =
    listActive(tasks.filter(_.userId === userId))

  override def add(task: TaskItem): Future[TaskItem] =
    insertReturningId(tasks, mapper.toRow(task)).map(newId => task.copy(id = newId))

  override def update(task: TaskItem): Future[Option[TaskItem]] = {
    val row = mapper.toRow(task)
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
