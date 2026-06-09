package repositories

import domain.user.User

/** User verilerine erisim sozlesmesi (port). Sorgular varsayilan: silinmemisler. */
trait UserRepository {

  /** Silinmemis tum kullanicilar. */
  def list(): Seq[User]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Option[User]

  /** Email ile getirir (silinmemis); yoksa None. (Ilerde login icin.) */
  def findByEmail(email: String): Option[User]

  /** Yeni kullanici ekler; atanmis id ile doner. */
  def add(user: User): User

  /** Var olan kullaniciyi gunceller; yoksa None. */
  def update(user: User): Option[User]
}
