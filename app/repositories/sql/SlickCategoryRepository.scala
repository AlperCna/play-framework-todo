package repositories.sql

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider

import domain.category.Category
import pagination.{Page, PageRequest}
import persistence.db.mappers.CategoryMappers._ // RowMapper[Category, CategoryRow] implicit instance'i
import persistence.db.{CategoryRow, RowMapper}
import repositories.interfaces.CategoryRepository

/** [[CategoryRepository]]'nin Slick (SQL Server) implementasyonu (ortak CRUD [[SlickCrudSupport]]'tan). */
@Singleton
class SlickCategoryRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends CategoryRepository
    with SlickCrudSupport {

  import profile.api._

  private val mapper = RowMapper[Category, CategoryRow]

  override def list(): Future[Seq[Category]] = listActive(categories)

  override def get(id: Long): Future[Option[Category]] = findOneActive(categories.filter(_.id === id))

  override def listByUser(userId: Long): Future[Seq[Category]] =
    listActive(categories.filter(_.userId === userId))

  override def listByUser(userId: Long, page: PageRequest): Future[Page[Category]] =
    pageActive(categories.filter(_.userId === userId), page)

  override def add(category: Category): Future[Category] =
    insertReturningId(categories, mapper.toRow(category)).map(newId => category.copy(id = newId))

  override def update(category: Category): Future[Option[Category]] = {
    val row = mapper.toRow(category)
    db.run(
      categories
        .filter(_.id === category.id)
        .map(c =>
          (c.name, c.description, c.userId, c.createdAt, c.createdBy, c.updatedAt, c.updatedBy, c.isDeleted, c.deletedAt, c.deletedBy)
        )
        .update(
          (row.name, row.description, row.userId, row.createdAt, row.createdBy, row.updatedAt, row.updatedBy, row.isDeleted, row.deletedAt, row.deletedBy)
        )
    ).map(n => if (n > 0) Some(category) else None)
  }
}
