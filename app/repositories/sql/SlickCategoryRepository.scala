package repositories.sql

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import domain.category.Category
import persistence.db.mappers.CategoryMappers._
import persistence.db.{CategoryRow, RowMapper, Tables}
import repositories.interfaces.CategoryRepository

/** [[CategoryRepository]]'nin Slick (SQL Server) implementasyonu. */
@Singleton
class SlickCategoryRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends CategoryRepository
    with HasDatabaseConfigProvider[JdbcProfile]
    with Tables {

  import profile.api._

  private val mapper = RowMapper[Category, CategoryRow]

  override def list(): Future[Seq[Category]] =
    db.run(categories.filter(!_.isDeleted).sortBy(_.id).result).map(_.map(mapper.toDomain))

  override def get(id: Long): Future[Option[Category]] =
    db.run(categories.filter(c => c.id === id && !c.isDeleted).result.headOption)
      .map(_.map(mapper.toDomain))

  override def listByUser(userId: Long): Future[Seq[Category]] =
    db.run(categories.filter(c => c.userId === userId && !c.isDeleted).sortBy(_.id).result)
      .map(_.map(mapper.toDomain))

  override def add(category: Category): Future[Category] = {
    val row = mapper.toRow(category)
    db.run((categories returning categories.map(_.id)) += row).map(newId => category.copy(id = newId))
  }

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
