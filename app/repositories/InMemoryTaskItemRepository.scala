package repositories

import javax.inject.{Inject, Singleton}

import domain.task.TaskItem
import persistence.Database

/**
 * [[TaskItemRepository]]'nin bellek-ici implementasyonu.
 *
 * Kendi deposunu tutmaz; tekil [[Database]] singleton'inin `tasks` tablosuna
 * delege eder. Boylece tum repo'lar ayni "DB"yi paylasir.
 */
@Singleton
class InMemoryTaskItemRepository @Inject() (db: Database) extends TaskItemRepository {

  override def list(): Seq[TaskItem] = db.tasks.all()

  override def get(id: Long): Option[TaskItem] = db.tasks.findById(id)

  override def add(task: TaskItem): TaskItem = db.tasks.add(task)

  override def update(task: TaskItem): Option[TaskItem] =
    db.tasks.findById(task.id, includeDeleted = true).map { _ =>
      db.tasks.put(task)
      task
    }

  override def listByUser(userId: Long): Seq[TaskItem] =
    db.tasks.find(_.userId.contains(userId))
}
