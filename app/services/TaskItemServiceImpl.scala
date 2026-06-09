package services

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

import domain.category.Category
import domain.common.{DomainError, Priority}
import domain.task.{TaskItem, TaskItemCategory}
import repositories.{CategoryRepository, TaskItemCategoryRepository, TaskItemRepository}

/**
 * [[TaskItemService]]'in implementasyonu.
 *
 * Cok-repolu islerin (orn. `assignToCategory`) koordine edildigi yer burasi.
 * Audit "by" suanlik sabit bir placeholder; gercek auth gelince bir
 * `CurrentUserProvider` portuna donusur.
 */
@Singleton
class TaskItemServiceImpl @Inject() (
    taskRepo: TaskItemRepository,
    categoryRepo: CategoryRepository,
    linkRepo: TaskItemCategoryRepository,
    clock: Clock
) extends TaskItemService {

  private val AuditUser = "system"

  override def list(): Seq[TaskItem] = taskRepo.list()

  override def listByUser(userId: Long): Seq[TaskItem] = taskRepo.listByUser(userId)

  override def get(id: Long): Option[TaskItem] = taskRepo.get(id)

  override def create(
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate],
      userId: Long
  ): Either[DomainError, TaskItem] =
    TaskItem
      .create(title, description, priority, dueDate, userId, clock.now, AuditUser)
      .map(taskRepo.add)

  override def update(
      id: Long,
      title: String,
      description: Option[String],
      priority: Priority,
      dueDate: Option[LocalDate]
  ): Either[DomainError, TaskItem] =
    for {
      existing <- found(id)
      edited <- existing.edit(title, description, priority, dueDate)
      saved <- persist(edited.markUpdated(clock.now, AuditUser))
    } yield saved

  override def complete(id: Long): Either[DomainError, TaskItem] =
    for {
      task <- found(id)
      completed <- task.complete(clock.today, clock.now)
      saved <- persist(completed)
    } yield saved

  override def reopen(id: Long): Either[DomainError, TaskItem] =
    for {
      task <- found(id)
      saved <- persist(task.reopen())
    } yield saved

  override def delete(id: Long): Either[DomainError, TaskItem] =
    for {
      task <- found(id)
      saved <- persist(task.softDeleteWithUser(AuditUser, clock.now))
    } yield saved

  override def assignToCategory(
      taskId: Long,
      categoryId: Long
  ): Either[DomainError, Option[TaskItemCategory]] =
    for {
      task <- found(taskId)
      category <- categoryRepo.get(categoryId).toRight(DomainError.NotFound("Category", categoryId))
      existing = linkRepo.listByTask(taskId)
      maybeLink <- task.assignToCategory(category, existing, clock.now, AuditUser)
    } yield maybeLink.map(linkRepo.add)

  override def removeFromCategory(taskId: Long, categoryId: Long): Either[DomainError, Unit] =
    for {
      task <- found(taskId)
      existing = linkRepo.listByTask(taskId)
      _ = task.removeFromCategory(categoryId, existing, clock.now, AuditUser).foreach(linkRepo.update)
    } yield ()

  override def categoriesOf(taskId: Long): Seq[Category] =
    linkRepo.listByTask(taskId).flatMap(link => categoryRepo.get(link.categoryId))

  // --- Yardimcilar: tekrar eden "bulunamadi" akisini sadelestirir ---
  private def found(id: Long): Either[DomainError, TaskItem] =
    taskRepo.get(id).toRight(DomainError.NotFound("TaskItem", id))

  private def persist(task: TaskItem): Either[DomainError, TaskItem] =
    taskRepo.update(task).toRight(DomainError.NotFound("TaskItem", task.id))
}
