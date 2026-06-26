package todo.task.application

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import todo.category.application.CategoryRepository
import todo.category.domain.Category
import todo.shared.application.Clock
import todo.shared.domain.DomainError
import todo.shared.application.Page
import todo.shared.application.PageRequest
import todo.shared.domain.Priority
import todo.shared.application.ServiceResult
import todo.task.domain.{TaskItem, TaskItemCategory}

@Singleton
class TaskItemServiceImpl @Inject() (
    taskRepo: TaskItemRepository,
    categoryRepo: CategoryRepository,
    linkRepo: TaskItemCategoryRepository,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends TaskItemService {

  private val AuditUser = "system"

  override def list(): Future[Seq[TaskItem]] = taskRepo.list()

  override def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]] =
    taskRepo.listByUser(userId, page)

  override def hasCompletedByUser(userId: Long): Future[Boolean] =
    taskRepo.hasCompletedByUser(userId)

  override def get(id: Long): Future[Option[TaskItem]] = taskRepo.get(id)

  override def create(
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate],
      userId: Long
  ): Future[Either[DomainError, TaskItem]] =
    ServiceResult
      .fromEither(TaskItem.create(title, description, priority, dueDate, userId, clock.now, AuditUser))
      .flatMap(task => ServiceResult.fromFuture(taskRepo.add(task)))
      .value

  override def update(
      id: Long,
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate]
  ): Future[Either[DomainError, TaskItem]] =
    (for {
      existing <- found(id)
      edited   <- ServiceResult.fromEither(existing.edit(title, description, priority, dueDate))
      saved    <- persist(edited.markUpdated(clock.now, AuditUser))
    } yield saved).value

  override def complete(id: Long): Future[Either[DomainError, TaskItem]] =
    (for {
      task      <- found(id)
      completed <- ServiceResult.fromEither(task.complete(clock.today, clock.now))
      saved     <- persist(completed)
    } yield saved).value

  override def reopen(id: Long): Future[Either[DomainError, TaskItem]] =
    (for {
      task  <- found(id)
      saved <- persist(task.reopen())
    } yield saved).value

  override def delete(id: Long): Future[Either[DomainError, TaskItem]] =
    (for {
      task  <- found(id)
      saved <- persist(task.softDeleteWithUser(AuditUser, clock.now))
    } yield saved).value

  override def purgeCompleted(): Future[Int] =
    taskRepo.list().flatMap { tasks =>
      val completed = tasks.filter(_.isCompleted)
      Future
        .traverse(completed)(task => taskRepo.update(task.softDeleteWithUser(AuditUser, clock.now)))
        .map(_.count(_.isDefined))
    }

  override def assignToCategory(
      taskId: Long,
      categoryId: Long
  ): Future[Either[DomainError, Option[TaskItemCategory]]] =
    (for {
      task     <- found(taskId)
      category <- ServiceResult.fromOptionF(
                    categoryRepo.get(categoryId),
                    DomainError.NotFound("Category", categoryId)
                  )
      existing  <- ServiceResult.fromFuture(linkRepo.listByTask(taskId))
      maybeLink <- ServiceResult.fromEither(task.assignToCategory(category, existing, clock.now, AuditUser))
      result <- maybeLink match {
                  case Some(link) =>
                    ServiceResult.fromFuture(linkRepo.add(link)).map(saved => Some(saved): Option[TaskItemCategory])
                  case None =>
                    ServiceResult.pure(Option.empty[TaskItemCategory])
                }
    } yield result).value

  override def removeFromCategory(taskId: Long, categoryId: Long): Future[Either[DomainError, Unit]] =
    (for {
      task     <- found(taskId)
      existing <- ServiceResult.fromFuture(linkRepo.listByTask(taskId))
      _ <- task.removeFromCategory(categoryId, existing, clock.now, AuditUser) match {
             case Some(deleted) => ServiceResult.fromFuture(linkRepo.update(deleted)).map(_ => ())
             case None          => ServiceResult.pure(())
           }
    } yield ()).value

  override def categoriesOf(taskId: Long): Future[Seq[Category]] =
    linkRepo.listByTask(taskId).flatMap { links =>
      Future.sequence(links.map(link => categoryRepo.get(link.categoryId))).map(_.flatten)
    }

  private def found(id: Long): ServiceResult[TaskItem] =
    ServiceResult.fromOptionF(taskRepo.get(id), DomainError.NotFound("TaskItem", id))

  private def persist(task: TaskItem): ServiceResult[TaskItem] =
    ServiceResult.fromOptionF(taskRepo.update(task), DomainError.NotFound("TaskItem", task.id))
}
