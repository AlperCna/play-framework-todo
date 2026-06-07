package repositories

import models.Todo

/**
 * Todo verilerine erisim icin soyutlama (interface).
 *
 * Controller'lar dogrudan bir veri kaynagina degil, bu trait'e bagimli olur.
 * Su an bellek-ici (in-memory) bir implementasyon kullaniyoruz; ilerde bir
 * veritabani (orn. Slick + H2) implementasyonu yazip Guice binding'ini
 * degistirerek gecis yapabilirsin. Controller kodu hic degismez.
 *
 * Not: Metotlar simdilik senkron (dogrudan deger donuyor) cunku ogrenmeyi
 * sade tutmak istiyoruz. Gercek bir veritabaninda bu metotlar muhtemelen
 * `Future[...]` dondururdu.
 */
trait TodoRepository {

  /** Tum todo'lari getirir. */
  def list(): Seq[Todo]

  /** Verilen id'ye sahip todo'yu getirir; yoksa None. */
  def get(id: Long): Option[Todo]

  /** Yeni bir todo olusturur ve olusturulan kaydi (atanmis id ile) doner. */
  def create(title: String, completed: Boolean): Todo

  /** Var olan bir todo'yu gunceller; kayit yoksa None. */
  def update(id: Long, title: String, completed: Boolean): Option[Todo]

  /** Verilen id'deki todo'yu siler. Silindiyse true, kayit yoksa false. */
  def delete(id: Long): Boolean
}
