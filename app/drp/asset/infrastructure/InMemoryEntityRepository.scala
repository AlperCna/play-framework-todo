package drp.asset.infrastructure

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Singleton

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import drp.asset.application.ports.EntityRepository
import drp.asset.domain.Entity

/** In-memory `EntityRepository` for DB-less service tests. Self-contained (no todo scaffold). */
@Singleton
class InMemoryEntityRepository extends EntityRepository {

  private val store = new TrieMap[Long, Entity]()
  private val seq   = new AtomicLong(0L)

  override def create(entity: Entity): Future[Entity] = {
    val id    = seq.incrementAndGet()
    val now   = Instant.now()
    val saved = entity.copy(id = id, createdAt = now, updatedAt = now)
    store.put(id, saved)
    Future.successful(saved)
  }

  override def listAll(): Future[Seq[Entity]] =
    Future.successful(store.values.toSeq.sortBy(_.id))

  override def existsById(id: Long): Future[Boolean] =
    Future.successful(store.contains(id))
}
