package drp.discovery.infrastructure.inmemory

import java.util.concurrent.atomic.AtomicLong

import javax.inject.{Inject, Singleton}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

import drp.discovery.application.DiscoveryStatusFilter
import drp.discovery.application.ports.DiscoveryRepository
import drp.discovery.domain.{CandidateDiscovery, DiscoveryId, NormalizedValue}
import drp.shared.application.{Page, PageRequest}

/**
 * In-memory `DiscoveryRepository` for DB-less service tests. Enforces the
 * `(entityId, normalizedValue)` uniqueness invariant that the DB unique index provides in
 * production.
 */
@Singleton
class InMemoryDiscoveryRepository @Inject() (implicit ec: ExecutionContext) extends DiscoveryRepository {

  private val store = TrieMap.empty[Long, CandidateDiscovery]
  private val seq   = new AtomicLong(0L)

  override def save(discovery: CandidateDiscovery): Future[CandidateDiscovery] = Future {
    val id    = seq.incrementAndGet()
    val saved = discovery.copy(id = DiscoveryId(id))
    store.put(id, saved)
    saved
  }

  override def saveAll(discoveries: Seq[CandidateDiscovery]): Future[Seq[CandidateDiscovery]] =
    Future.sequence(discoveries.map(save))

  override def get(id: DiscoveryId): Future[Option[CandidateDiscovery]] =
    Future.successful(store.get(id.value))

  override def findByEntityAndNormalized(
      entityId: Long,
      normalizedValue: NormalizedValue
  ): Future[Option[CandidateDiscovery]] =
    Future.successful(
      store.values.find(d => d.entityId == entityId && d.normalizedValue.value == normalizedValue.value)
    )

  override def listNormalizedValuesByEntity(entityId: Long): Future[Set[String]] =
    Future.successful(
      store.values.filter(_.entityId == entityId).map(_.normalizedValue.value).toSet
    )

  override def listByEntity(
      entityId: Long,
      statusFilter: Option[DiscoveryStatusFilter],
      page: PageRequest
  ): Future[Page[CandidateDiscovery]] = Future.successful {
    val all = store.values
      .filter(_.entityId == entityId)
      .filter(applyFilter(statusFilter))
      .toSeq
      .sortBy(_.createdAt)(Ordering[java.time.Instant].reverse)
    val items = all.slice(page.offset, page.offset + page.size)
    Page(items, page.page, page.size, all.size.toLong)
  }

  private def applyFilter(filter: Option[DiscoveryStatusFilter])(d: CandidateDiscovery): Boolean =
    filter match {
      case None => true
      case Some(DiscoveryStatusFilter.PendingValidation) =>
        d.dnsStatus == drp.discovery.domain.DnsStatus.Pending && d.skipReason.isEmpty
      case Some(DiscoveryStatusFilter.Whitelisted) =>
        d.skipReason.contains(drp.discovery.domain.SkipReason.Whitelisted)
      case Some(DiscoveryStatusFilter.InvalidFormat) =>
        d.skipReason.contains(drp.discovery.domain.SkipReason.InvalidFormat)
    }
}
