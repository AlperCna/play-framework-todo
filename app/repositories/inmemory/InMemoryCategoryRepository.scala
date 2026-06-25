package repositories.inmemory

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

import domain.category.Category
import pagination.{Page, PageRequest}
import persistence.inmemory.Database
import repositories.interfaces.CategoryRepository

/** [[CategoryRepository]]'nin bellek-ici implementasyonu; [[persistence.inmemory.Database]]'e delege eder. */
@Singleton
class InMemoryCategoryRepository @Inject() (db: Database) extends CategoryRepository {

  override def list(): Future[Seq[Category]] = Future.successful(db.categories.all())

  override def get(id: Long): Future[Option[Category]] = Future.successful(db.categories.findById(id))

  override def listByUser(userId: Long): Future[Seq[Category]] =
    Future.successful(db.categories.find(_.userId == userId))

  override def listByUser(userId: Long, page: PageRequest): Future[Page[Category]] = {
    val all    = db.categories.find(_.userId == userId) // id'ye sirali, soft-delete haric
    val window = all.slice(page.offset.toInt, page.offset.toInt + page.limit)
    Future.successful(Page.from(window, page, all.size.toLong))
  }

  override def add(category: Category): Future[Category] = Future.successful(db.categories.add(category))

  override def update(category: Category): Future[Option[Category]] =
    Future.successful(db.categories.findById(category.id, includeDeleted = true).map { _ =>
      db.categories.put(category)
      category
    })
}
