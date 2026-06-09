package repositories

import javax.inject.{Inject, Singleton}

import domain.category.Category
import persistence.Database

/** [[CategoryRepository]]'nin bellek-ici implementasyonu; [[Database]]'e delege eder. */
@Singleton
class InMemoryCategoryRepository @Inject() (db: Database) extends CategoryRepository {

  override def list(): Seq[Category] = db.categories.all()

  override def get(id: Long): Option[Category] = db.categories.findById(id)

  override def listByUser(userId: Long): Seq[Category] =
    db.categories.find(_.userId == userId)

  override def add(category: Category): Category = db.categories.add(category)

  override def update(category: Category): Option[Category] =
    db.categories.findById(category.id, includeDeleted = true).map { _ =>
      db.categories.put(category)
      category
    }
}
