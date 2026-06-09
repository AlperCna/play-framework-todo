package repositories

import domain.category.Category

/** Category verilerine erisim sozlesmesi (port). Sorgular varsayilan: silinmemisler. */
trait CategoryRepository {

  /** Silinmemis tum kategoriler. */
  def list(): Seq[Category]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Option[Category]

  /** Bir kullanicinin silinmemis kategorileri. */
  def listByUser(userId: Long): Seq[Category]

  /** Yeni kategori ekler; atanmis id ile doner. */
  def add(category: Category): Category

  /** Var olan kategoriyi gunceller; yoksa None. */
  def update(category: Category): Option[Category]
}
