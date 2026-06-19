package persistence.db

import slick.jdbc.JdbcProfile

/**
 * Slick "cake"inin paylasilan koku: `profile` + tum tablolarin ortak tabani
 * [[BaseTable]] (`id` + `isDeleted`).
 *
 * Her tablo bir FRAGMENT trait'idir (`*Table`) ve bunu extend eder; hepsi [[Tables]]
 * cephesinde birlesir. Ayri trait olmasinin sebebi: tum fragment'lerin VE generic
 * CRUD'un (`SlickCrudSupport`) AYNI path-dependent `BaseTable` tipini paylasmasi
 * (boylece tek tip olarak gorunur, generic filtre yazilabilir).
 */
trait BaseTables {

  protected val profile: JdbcProfile
  import profile.api._

  /**
   * Tum tablolarin paylastigi ortak kolonlar: kimlik (`id`) ve soft-delete bayragi
   * (`isDeleted`). Generic CRUD bu iki kolon uzerinden tablodan-bagimsiz, tip-guvenli
   * sorgu yazabilir.
   */
  abstract class BaseTable[R](tag: Tag, name: String) extends Table[R](tag, name) {
    def id: Rep[Long]
    def isDeleted: Rep[Boolean]
  }
}
