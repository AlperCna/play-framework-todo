package todo.user.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider

import todo.shared.infrastructure.RowMapper
import todo.shared.infrastructure.SlickCrudSupport
import todo.user.application.UserRepository
import todo.user.domain.User
import todo.user.infrastructure.UserMappers._

@Singleton
class SlickUserRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(
    implicit ec: ExecutionContext
) extends UserRepository
    with SlickCrudSupport {

  import profile.api._

  private val mapper = RowMapper[User, UserRow]

  override def list(): Future[Seq[User]] = listActive(users)

  override def get(id: Long): Future[Option[User]] = findOneActive(users.filter(_.id === id))

  override def findByEmail(email: String): Future[Option[User]] =
    findOneActive(users.filter(_.email.toLowerCase === email.toLowerCase))

  override def add(user: User): Future[User] =
    insertReturningId(users, mapper.toRow(user)).map(newId => user.copy(id = newId))

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
