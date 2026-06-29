package drp.discovery.application.ports

import scala.concurrent.Future

import drp.discovery.application.DiscoveryStatusFilter
import drp.discovery.domain.{CandidateDiscovery, DiscoveryId, NormalizedValue}
import drp.shared.application.{Page, PageRequest}

/** Persistence boundary for candidate_discoveries. Single-writer: DiscoveryIntakeService is the only caller. */
trait DiscoveryRepository {

  /** Persist a new discovery (id = 0 on input; returns the saved record with DB-assigned id). */
  def save(discovery: CandidateDiscovery): Future[CandidateDiscovery]

  /** Bulk-persist a batch of new discoveries in a single round-trip; returns saved records in stable order. */
  def saveAll(discoveries: Seq[CandidateDiscovery]): Future[Seq[CandidateDiscovery]]

  /** Look up a single discovery by id. */
  def get(id: DiscoveryId): Future[Option[CandidateDiscovery]]

  /**
   * Find an existing discovery by entity + normalized value.
   * Used for the duplicate guard before attempting a save.
   */
  def findByEntityAndNormalized(entityId: Long, normalizedValue: NormalizedValue): Future[Option[CandidateDiscovery]]

  /**
   * Bulk-fetch all normalized values for an entity (used to deduplicate a permutation batch in-memory).
   * Returns only the normalized-value strings — not full records — to keep the payload small.
   */
  def listNormalizedValuesByEntity(entityId: Long): Future[Set[String]]

  /**
   * Paginated list for a given entity, with optional status filter.
   * None = no filter (show all). Ordered reverse-chronologically (created_at DESC).
   */
  def listByEntity(
      entityId: Long,
      statusFilter: Option[DiscoveryStatusFilter],
      page: PageRequest
  ): Future[Page[CandidateDiscovery]]
}
