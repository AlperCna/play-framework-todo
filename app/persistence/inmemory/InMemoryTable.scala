package persistence.inmemory

import java.util.concurrent.atomic.AtomicLong

import scala.collection.concurrent.TrieMap

import domain.common.AuditableEntity

/**
 * Bellek-ici, soft-delete farkinda, generic bir "tablo".
 *
 * Dort entity de ayni saklama/id-uretme/soft-delete-filtreleme mantigini
 * paylasir; bu mantigi tek bir yerde toplayarak tekrari onleriz.
 *
 * Thread-safety: es zamanli istekler icin `TrieMap` + `AtomicLong`.
 *
 * @param withId Entity'ye id atayan fonksiyon. Bir trait `copy`'nin somut tipini
 *               ifade edemedigi icin id atamayi disaridan (`(t, id) => t.copy(id = id)`)
 *               aliriz.
 * @tparam A Saklanan entity tipi (kimlik + audit tasidigi icin `AuditableEntity`).
 */
final class InMemoryTable[A <: AuditableEntity](withId: (A, Long) => A) {

  private val store = TrieMap.empty[Long, A]
  private val idSequence = new AtomicLong(0L)

  /** Yeni kayit: id uretir, atar, saklar ve kaydedilmis hali doner. */
  def add(entity: A): A = {
    val id = idSequence.incrementAndGet()
    val saved = withId(entity, id)
    store.put(id, saved)
    saved
  }

  /** Var olan bir kaydin tamamini uzerine yazar (update). */
  def put(entity: A): Unit =
    store.put(entity.id, entity)

  /** id ile getirir; varsayilan olarak silinmis kayitlari ELEMEZ disinda tutar. */
  def findById(id: Long, includeDeleted: Boolean = false): Option[A] =
    store.get(id).filter(a => includeDeleted || !a.isDeleted)

  /** Tum kayitlar (id'ye gore sirali); varsayilan olarak silinmemisler. */
  def all(includeDeleted: Boolean = false): Seq[A] =
    store.values.toSeq.filter(a => includeDeleted || !a.isDeleted).sortBy(_.id)

  /** Verilen kosula uyan kayitlar (varsayilan: silinmemisler). */
  def find(p: A => Boolean, includeDeleted: Boolean = false): Seq[A] =
    all(includeDeleted).filter(p)
}
