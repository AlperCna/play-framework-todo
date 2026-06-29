package drp.asset.infrastructure.inmemory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import drp.asset.application.ports.AssetGroupRepository
import drp.asset.domain.{AssetGroup, AssetGroupId, EntityId}
import drp.shared.application.Clock

/** In-memory `AssetGroupRepository` for DB-less service tests. */
@Singleton
class InMemoryAssetGroupRepository @Inject() (clock: Clock)(implicit ec: ExecutionContext) extends AssetGroupRepository {

  private val store = new ConcurrentHashMap[Long, AssetGroup]()
  private val seq = new AtomicLong(0L)

  override def add(g: AssetGroup): Future[AssetGroup] = Future {
    val id = seq.incrementAndGet()
    val now = clock.now()
    val saved = g.copy(id = AssetGroupId(id), createdAt = now, updatedAt = now)
    store.put(id, saved)
    saved
  }

  override def get(id: AssetGroupId): Future[Option[AssetGroup]] = Future.successful(Option(store.get(id.value)))

  override def existsByEntityAndName(entityId: EntityId, name: String): Future[Boolean] =
    Future.successful(store.values.asScala.exists(g => g.entityId == entityId && g.name.equalsIgnoreCase(name.trim)))

  override def update(g: AssetGroup): Future[Option[AssetGroup]] = Future.successful {
    Option(store.get(g.id.value)).map { existing =>
      val updated = g.copy(createdAt = existing.createdAt, updatedAt = clock.now())
      store.put(g.id.value, updated)
      updated
    }
  }

  override def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]] =
    Future.successful(store.values.asScala.toSeq.filter(_.entityId == entityId).sortBy(_.id.value))
}
