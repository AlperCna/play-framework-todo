package todo.user.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.Future

import todo.shared.infrastructure.Database
import todo.user.application.UserRepository
import todo.user.domain.User

@Singleton
class InMemoryUserRepository @Inject() (db: Database) extends UserRepository {

  override def list(): Future[Seq[User]] = Future.successful(db.users.all())

  override def get(id: Long): Future[Option[User]] = Future.successful(db.users.findById(id))

  override def findByEmail(email: String): Future[Option[User]] =
    Future.successful(db.users.find(_.email.equalsIgnoreCase(email)).headOption)

  override def add(user: User): Future[User] = Future.successful(db.users.add(user))

  override def update(user: User): Future[Option[User]] =
    Future.successful(db.users.findById(user.id, includeDeleted = true).map { _ =>
      db.users.put(user)
      user
    })
}
