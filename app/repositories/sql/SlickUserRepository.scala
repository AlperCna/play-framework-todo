package repositories.sql

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import domain.user.User
import persistence.db.mappers.UserMappers._
import persistence.db.{RowMapper, Tables, UserRow}
import repositories.interfaces.UserRepository

/**
 * [[UserRepository]]'nin Slick (SQL Server) implementasyonu.
 *
 * `Tables` (duz row + tablo tanimlari) ile karilir; satir<->domain cevrimi
 * [[RowMapper]] instance'i (`UserMappers`) uzerinden yapilir. Sorgular varsayilan
 * olarak silinmemisleri doner.
 */
@Singleton
class SlickUserRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends UserRepository
    with HasDatabaseConfigProvider[JdbcProfile]
    with Tables {

  import profile.api._

  private val mapper = RowMapper[User, UserRow]

  override def list(): Future[Seq[User]] =
    db.run(users.filter(!_.isDeleted).sortBy(_.id).result).map(_.map(mapper.toDomain))

  override def get(id: Long): Future[Option[User]] =
    db.run(users.filter(u => u.id === id && !u.isDeleted).result.headOption).map(_.map(mapper.toDomain))

  override def findByEmail(email: String): Future[Option[User]] =
    db.run(
      users.filter(u => u.email.toLowerCase === email.toLowerCase && !u.isDeleted).result.headOption
    ).map(_.map(mapper.toDomain))

  override def add(user: User): Future[User] = {
    val row = mapper.toRow(user)
    db.run((users returning users.map(_.id)) += row).map(newId => user.copy(id = newId))
  }

  override def update(user: User): Future[Option[User]] = {
    val row = mapper.toRow(user)
    db.run(
      users
        .filter(_.id === user.id)
        .map(u =>
          (u.email, u.password, u.createdAt, u.createdBy, u.updatedAt, u.updatedBy, u.isDeleted, u.deletedAt, u.deletedBy)
        )
        .update(
          (row.email, row.password, row.createdAt, row.createdBy, row.updatedAt, row.updatedBy, row.isDeleted, row.deletedAt, row.deletedBy)
        )
    ).map(n => if (n > 0) Some(user) else None)
  }
}
