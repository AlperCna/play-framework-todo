package todo.user.application

import scala.concurrent.Future

import todo.shared.application.CrudRepository
import todo.user.domain.User

trait UserRepository extends CrudRepository[User] {

  def findByEmail(email: String): Future[Option[User]]
}
