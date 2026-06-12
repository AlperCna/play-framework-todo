package repositories.interfaces

import scala.concurrent.Future

import domain.user.User

/** User verilerine erisim sozlesmesi (port). Sorgular varsayilan: silinmemisler. */
trait UserRepository {

  /** Silinmemis tum kullanicilar. */
  def list(): Future[Seq[User]]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Future[Option[User]]

  /** Email ile getirir (silinmemis); yoksa None. */
  def findByEmail(email: String): Future[Option[User]]

  /** Yeni kullanici ekler; atanmis id ile doner. */
  def add(user: User): Future[User]

  /** Var olan kullaniciyi gunceller; yoksa None. */
  def update(user: User): Future[Option[User]]
}
