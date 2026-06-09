package repositories

import javax.inject.{Inject, Singleton}

import domain.user.User
import persistence.Database

/** [[UserRepository]]'nin bellek-ici implementasyonu; [[Database]]'e delege eder. */
@Singleton
class InMemoryUserRepository @Inject() (db: Database) extends UserRepository {

  override def list(): Seq[User] = db.users.all()

  override def get(id: Long): Option[User] = db.users.findById(id)

  override def findByEmail(email: String): Option[User] =
    db.users.find(_.email.equalsIgnoreCase(email)).headOption

  override def add(user: User): User = db.users.add(user)

  override def update(user: User): Option[User] =
    db.users.findById(user.id, includeDeleted = true).map { _ =>
      db.users.put(user)
      user
    }
}
