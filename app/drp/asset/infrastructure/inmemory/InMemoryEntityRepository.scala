package drp.asset.infrastructure.inmemory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import drp.asset.application.ports.EntityRepository
import drp.asset.domain.{Entity, EntityId}
import drp.shared.application.{Clock, Page, PageRequest}

/**
 * In-memory `EntityRepository` for DB-less service tests. Simulates the DB's id assignment and the
 * `set_updated_at` trigger (add sets both timestamps; update moves `updatedAt`, keeps `createdAt`).
 */
@Singleton
class InMemoryEntityRepository @Inject() (clock: Clock)(implicit ec: ExecutionContext) extends EntityRepository {

  private val store = new ConcurrentHashMap[Long, Entity]()
  private val seq = new AtomicLong(0L)

  override def add(e: Entity): Future[Entity] = Future {
    val id = seq.incrementAndGet()
    val now = clock.now()
    val saved = e.copy(id = EntityId(id), createdAt = now, updatedAt = now)
    store.put(id, saved)
    saved
  }

  override def get(id: EntityId): Future[Option[Entity]] = Future.successful(Option(store.get(id.value)))

  override def existsById(id: EntityId): Future[Boolean] = Future.successful(store.containsKey(id.value))

  override def existsByName(name: String): Future[Boolean] =
    Future.successful(store.values.asScala.exists(_.name.equalsIgnoreCase(name.trim)))

  override def update(e: Entity): Future[Option[Entity]] = Future.successful {
    Option(store.get(e.id.value)).map { existing =>
      val updated = e.copy(createdAt = existing.createdAt, updatedAt = clock.now())
      store.put(e.id.value, updated)
      updated
    }
  }

  override def list(page: PageRequest): Future[Page[Entity]] = Future.successful {
    val all = store.values.asScala.toSeq.sortBy(_.id.value)
    val items = all.slice(page.offset, page.offset + page.size)
    Page(items, page.page, page.size, all.size.toLong)
  }
}
