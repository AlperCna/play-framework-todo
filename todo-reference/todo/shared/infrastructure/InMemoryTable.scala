package todo.shared.infrastructure

import java.util.concurrent.atomic.AtomicLong

import scala.collection.concurrent.TrieMap

import todo.shared.domain.AuditableEntity

final class InMemoryTable[A <: AuditableEntity](withId: (A, Long) => A) {

  private val store = TrieMap.empty[Long, A]
  private val idSequence = new AtomicLong(0L)

  def add(entity: A): A = {
    val id = idSequence.incrementAndGet()
    val saved = withId(entity, id)
    store.put(id, saved)
    saved
  }

  def put(entity: A): Unit =
    store.put(entity.id, entity)

  def findById(id: Long, includeDeleted: Boolean = false): Option[A] =
    store.get(id).filter(a => includeDeleted || !a.isDeleted)

  def all(includeDeleted: Boolean = false): Seq[A] =
    store.values.toSeq.filter(a => includeDeleted || !a.isDeleted).sortBy(_.id)

  def find(p: A => Boolean, includeDeleted: Boolean = false): Seq[A] =
    all(includeDeleted).filter(p)
}
