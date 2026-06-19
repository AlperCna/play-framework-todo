package repositories.interfaces

import scala.concurrent.Future

/**
 * Tekduze entity'lerin paylastigi standart CRUD sekli (port).
 *
 * OPT-IN: yalnizca bu sekle UYAN repo'lar (User/Task/Category) genisletir; ozel
 * sekilli olanlar (orn. join entity [[TaskItemCategoryRepository]]) bunu
 * genisletmez (Interface Segregation). Id tipi tum entity'lerde `Long` oldugundan
 * `[A, ID]` yerine sade `[A]` kullanilir.
 *
 * DONUS TIPI SOZLESMESI: bir repo'nun "basarisizlik"lari yalnizca (1) yokluk ->
 * `Option`/bos `Seq`, ve (2) altyapi hatasi -> failed `Future`'dir. Bunlarin
 * hicbiri DOMAIN hatasi degildir; `DomainError` yalnizca domain'den (saf `Either`)
 * gelir ve servis katmaninda birlestirilir. Bu yuzden repo imzalarinda
 * `Future[Either[DomainError, A]]` YOKTUR.
 */
trait CrudRepository[A] {

  /** Silinmemis tum kayitlar. */
  def list(): Future[Seq[A]]

  /** id ile getirir (silinmemis); yoksa None. */
  def get(id: Long): Future[Option[A]]

  /** Yeni kayit ekler; atanmis id ile kaydedilmis hali doner. */
  def add(entity: A): Future[A]

  /** Var olan kaydi (tum entity'yi) gunceller; kayit yoksa None. */
  def update(entity: A): Future[Option[A]]
}
