package drp.asset.infrastructure.inmemory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import drp.asset.application.ports.ExclusionRepository
import drp.asset.domain.{EntityId, Exclusion, ExclusionId, MatchType}
import drp.shared.application.Clock

/** In-memory `ExclusionRepository` for DB-less service tests. */
@Singleton
class InMemoryExclusionRepository @Inject() (clock: Clock)(implicit ec: ExecutionContext) extends ExclusionRepository {

  private val store = new ConcurrentHashMap[Long, Exclusion]()
  private val seq = new AtomicLong(0L)

  override def add(x: Exclusion): Future[Exclusion] = Future {
    val id = seq.incrementAndGet()
    val now = clock.now()
    val saved = x.copy(id = ExclusionId(id), createdAt = now, updatedAt = now)
    store.put(id, saved)
    saved
  }

  override def get(id: ExclusionId): Future[Option[Exclusion]] = Future.successful(Option(store.get(id.value)))

  override def existsActive(entityId: EntityId, value: String, matchType: MatchType): Future[Boolean] =
    Future.successful(store.values.asScala.exists { x =>
      x.isActive && x.entityId == entityId && x.value == value.trim && x.matchType == matchType
    })

  override def update(x: Exclusion): Future[Option[Exclusion]] = Future.successful {
    Option(store.get(x.id.value)).map { existing =>
      val updated = x.copy(createdAt = existing.createdAt, updatedAt = clock.now())
      store.put(x.id.value, updated)
      updated
    }
  }

  override def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]] =
    Future.successful(store.values.asScala.toSeq.filter(x => x.isActive && x.entityId == entityId).sortBy(_.id.value))
}
