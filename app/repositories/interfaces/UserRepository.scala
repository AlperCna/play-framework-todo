package repositories.interfaces

import scala.concurrent.Future

import domain.user.User

/**
 * User verilerine erisim sozlesmesi (port).
 *
 * Standart CRUD [[CrudRepository]]'den; burada yalnizca User'a ozgu sorgu.
 * Sorgular varsayilan olarak silinmemisleri doner.
 */
trait UserRepository extends CrudRepository[User] {

  /** Email ile getirir (silinmemis); yoksa None. */
  def findByEmail(email: String): Future[Option[User]]
}
