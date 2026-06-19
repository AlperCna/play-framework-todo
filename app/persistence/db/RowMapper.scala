package persistence.db

/**
 * Zengin domain entity'si (`D`) <-> duz DB satiri (`R`) cevrimi icin type class.
 *
 * NEDEN TYPE CLASS? "domain != DB" cevrimi her entity icin AYNI sekle (`toRow` /
 * `toDomain`) sahiptir ama her entity'de FARKLI calisir. Bu, tipe gore degisen
 * tek bir operasyon; Essential Scala'nin idiomatik karsiligi type class'tir. Tek
 * sozlesme sayesinde:
 *   - okuma/yazma isimleri tum entity'lerde AYNI (eski `toUser`/`toLink` asimetrisi
 *     yok),
 *   - ileride `RowMapper[D, R]` uzerinden GENERIC bir CRUD repo yazilabilir.
 *
 * Instance'lar persistence tarafinda (`persistence.db.mappers.*Mappers`) durur;
 * domain, Row/JDBC tiplerini BILMEZ (persistence bilgisi domain'e sizmaz).
 */
trait RowMapper[D, R] {

  /** Zengin domain entity'sini duz Row'a duzlestirir (yazma yonu). */
  def toRow(domain: D): R

  /** Duz Row'dan zengin domain entity'sini yeniden kurar (okuma yonu). */
  def toDomain(row: R): D
}

object RowMapper {

  /** Summoner: `implicitly[RowMapper[D, R]]` yerine `RowMapper[D, R]`. */
  def apply[D, R](implicit m: RowMapper[D, R]): RowMapper[D, R] = m
}
