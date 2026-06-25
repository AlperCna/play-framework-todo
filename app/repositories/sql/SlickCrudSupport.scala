package repositories.sql

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile

import pagination.{Page, PageRequest}
import persistence.db.{RowMapper, Tables}

/**
 * Slick repo'larina KARILAN, soft-delete farkinda generic CRUD yapi taslari.
 *
 * `InMemoryTable[A]`'in gercek-DB karsiligi: ortak list/get/insert mantigini BIR
 * KEZ yazar. Her metot, tablonun `Tables.BaseTable` (id + isDeleted) oldugunu TIP
 * seviyesinde bildigi icin tablodan-bagimsiz filtre yazabilir. Type param'lar
 * METOT seviyesindedir; boylece path-dependent `BaseTable[R]` tipi sorunsuz
 * gorunur (class-seviyesi bound bunu ifade edemezdi).
 *
 * `update` BILEREK burada YOK: SQL Server IDENTITY kolonu update edilemedigi icin
 * her entity id-haric kendi projeksiyonunu gunceller (tam-generic'e direnen nokta).
 */
trait SlickCrudSupport extends HasDatabaseConfigProvider[JdbcProfile] with Tables {

  import profile.api._

  /** Aktif (silinmemis) kayitlar, id'ye sirali. On-filtrelenmis query de verilebilir. */
  protected def listActive[A, R, T <: BaseTable[R]](q: Query[T, R, Seq])(
      implicit m: RowMapper[A, R],
      ec: ExecutionContext
  ): Future[Seq[A]] =
    db.run(q.filter(!_.isDeleted).sortBy(_.id).result).map(_.map(m.toDomain))

  /** Aktif tek kayit (get / findByEmail / findActiveLink hepsi bunu kullanir). */
  protected def findOneActive[A, R, T <: BaseTable[R]](q: Query[T, R, Seq])(
      implicit m: RowMapper[A, R],
      ec: ExecutionContext
  ): Future[Option[A]] =
    db.run(q.filter(!_.isDeleted).result.headOption).map(_.map(m.toDomain))

  /**
   * Aktif (silinmemis) kayitlarin BIR SAYFASI + toplam adet.
   *
   * Iki sorgu calistirir: COUNT (toplam sayfa icin) ve LIMIT/OFFSET'li pencere.
   * Ikisini de `db.run` ile onceden baslatip (val'ler) PARALEL kosariz; sonra
   * [[pagination.Page]]'e birlestiririz. `listActive` gibi tablodan-bagimsiz:
   * `BaseTable` (id + isDeleted) tip seviyesinde bilindigi icin generic kalir.
   */
  protected def pageActive[A, R, T <: BaseTable[R]](q: Query[T, R, Seq], page: PageRequest)(
      implicit m: RowMapper[A, R],
      ec: ExecutionContext
  ): Future[Page[A]] = {
    val active = q.filter(!_.isDeleted)
    val window = active.sortBy(_.id).drop(page.offset).take(page.limit.toLong)
    val totalF = db.run(active.length.result) // COUNT(*)
    val rowsF  = db.run(window.result)        // SELECT ... OFFSET/FETCH
    for {
      total <- totalF
      rows  <- rowsF
    } yield Page.from(rows.map(m.toDomain), page, total.toLong)
  }

  /** Insert + uretilen IDENTITY id (AutoInc kolon insert'te otomatik dislanir). */
  protected def insertReturningId[R, T <: BaseTable[R]](q: TableQuery[T], row: R)(
      implicit ec: ExecutionContext
  ): Future[Long] =
    db.run((q returning q.map(_.id)) += row)
}
