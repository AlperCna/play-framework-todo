package repositories

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Singleton

import scala.collection.concurrent.TrieMap

import models.Todo

/**
 * [[TodoRepository]]'nin bellek-ici (in-memory) implementasyonu.
 *
 * Veriler uygulama belleginde tutulur, bu yuzden uygulama yeniden
 * baslatildiginda kaybolur. Birden fazla istegin ayni anda erisebilmesi
 * icin thread-safe yapilar kullaniyoruz:
 *   - TrieMap: es zamanli erisime uygun bir Map.
 *   - AtomicLong: id uretimini guvenli sekilde yapar.
 *
 * `@Singleton`: Guice bu sinifin tek bir ornegini olusturup paylasir;
 * boylece tum istekler ayni veri uzerinde calisir.
 */
@Singleton
class InMemoryTodoRepository extends TodoRepository {

  private val store = TrieMap.empty[Long, Todo]
  private val idSequence = new AtomicLong(0L)

  // Baslangicta birkac ornek kayit (istersen silebilirsin).
  create("Play framework ogren", completed = false)
  create("Todo uygulamasi yap", completed = true)

  override def list(): Seq[Todo] =
    store.values.toSeq.sortBy(_.id)

  override def get(id: Long): Option[Todo] =
    store.get(id)

  override def create(title: String, completed: Boolean): Todo = {
    val id = idSequence.incrementAndGet()
    val todo = Todo(id, title, completed)
    store.put(id, todo)
    todo
  }

  override def update(id: Long, title: String, completed: Boolean): Option[Todo] = {
    store.get(id).map { _ =>
      val updated = Todo(id, title, completed)
      store.put(id, updated)
      updated
    }
  }

  override def delete(id: Long): Boolean =
    store.remove(id).isDefined
}
