package repositories.inmemory

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

import domain.task.TaskItem
import pagination.{Page, PageRequest}
import persistence.inmemory.Database
import repositories.interfaces.TaskItemRepository

/**
 * [[TaskItemRepository]]'nin bellek-ici implementasyonu.
 *
 * Kendi deposunu tutmaz; tekil [[persistence.inmemory.Database]] singleton'inin
 * `tasks` tablosuna delege eder. Senkron `InMemoryTable` cagrilarini
 * `Future.successful` ile async imzaya uyarlar (testler bu yolu kullanir).
 */
@Singleton
class InMemoryTaskItemRepository @Inject() (db: Database) extends TaskItemRepository {

  override def list(): Future[Seq[TaskItem]] = Future.successful(db.tasks.all())

  override def get(id: Long): Future[Option[TaskItem]] = Future.successful(db.tasks.findById(id))

  override def add(task: TaskItem): Future[TaskItem] = Future.successful(db.tasks.add(task))

  override def update(task: TaskItem): Future[Option[TaskItem]] =
    Future.successful(db.tasks.findById(task.id, includeDeleted = true).map { _ =>
      db.tasks.put(task)
      task
    })

  override def listByUser(userId: Long, page: PageRequest): Future[Page[TaskItem]] = {
    val all    = db.tasks.find(_.userId.contains(userId)) // id'ye sirali, soft-delete haric
    val window = all.slice(page.offset.toInt, page.offset.toInt + page.limit)
    Future.successful(Page.from(window, page, all.size.toLong))
  }

  override def hasCompletedByUser(userId: Long): Future[Boolean] =
    Future.successful(db.tasks.find(_.userId.contains(userId)).exists(_.isCompleted))
}
