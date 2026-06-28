package drp.asset.infrastructure

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Singleton

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import drp.asset.application.ports.ExclusionRepository
import drp.asset.domain.{Exclusion, ExclusionMatchType}

/**
 * In-memory `ExclusionRepository` for DB-less service tests. Self-contained (no todo scaffold);
 * mirrors the DB defaults (active, `created_by = system`, timestamps) and the active-duplicate guard.
 */
@Singleton
class InMemoryExclusionRepository extends ExclusionRepository {

  private val store = new TrieMap[Long, Exclusion]()
  private val seq   = new AtomicLong(0L)

  override def create(exclusion: Exclusion): Future[Exclusion] = {
    val id    = seq.incrementAndGet()
    val now   = Instant.now()
    val saved = exclusion.copy(id = id, isActive = true, createdBy = "system", createdAt = now, updatedAt = now)
    store.put(id, saved)
    Future.successful(saved)
  }

  override def listByEntity(entityId: Long): Future[Seq[Exclusion]] =
    Future.successful(store.values.filter(_.entityId == entityId).toSeq.sortBy(_.id))

  override def existsActiveDuplicate(
      entityId: Long,
      value: String,
      matchType: ExclusionMatchType
  ): Future[Boolean] =
    Future.successful(
      store.values.exists(e => e.entityId == entityId && e.value == value && e.matchType == matchType && e.isActive)
    )
}
