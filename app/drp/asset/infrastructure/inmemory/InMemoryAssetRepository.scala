package drp.asset.infrastructure.inmemory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

import drp.asset.application.ports.AssetRepository
import drp.asset.domain.{Asset, AssetId, AssetType, EntityId}
import drp.shared.application.Clock

/** In-memory `AssetRepository` for DB-less service tests (simulates id assignment + the updated-at trigger). */
@Singleton
class InMemoryAssetRepository @Inject() (clock: Clock)(implicit ec: ExecutionContext) extends AssetRepository {

  private val store = new ConcurrentHashMap[Long, Asset]()
  private val seq = new AtomicLong(0L)

  override def add(a: Asset): Future[Asset] = Future {
    val id = seq.incrementAndGet()
    val now = clock.now()
    val saved = a.copy(id = AssetId(id), createdAt = now, updatedAt = now)
    store.put(id, saved)
    saved
  }

  override def get(id: AssetId): Future[Option[Asset]] = Future.successful(Option(store.get(id.value)))

  override def existsActive(entityId: EntityId, assetType: AssetType, value: String): Future[Boolean] =
    Future.successful(store.values.asScala.exists { a =>
      a.isActive && a.entityId == entityId && a.assetType == assetType && a.value == value.trim
    })

  override def update(a: Asset): Future[Option[Asset]] = Future.successful {
    Option(store.get(a.id.value)).map { existing =>
      val updated = a.copy(createdAt = existing.createdAt, updatedAt = clock.now())
      store.put(a.id.value, updated)
      updated
    }
  }

  override def listByEntity(entityId: EntityId): Future[Seq[Asset]] =
    Future.successful(store.values.asScala.toSeq.filter(_.entityId == entityId).sortBy(_.id.value))
}
