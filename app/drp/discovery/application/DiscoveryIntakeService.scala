package drp.discovery.application

import scala.concurrent.Future

import drp.discovery.domain.{CandidateDiscovery, DiscoveryId}
import drp.shared.application.{Page, PageRequest, ServiceResult}

/** Orchestrates manual intake and permutation intake through the same normalise → dedupe → exclusion-check → save pipeline. */
trait DiscoveryIntakeService {

  /**
   * Submit a suspicious domain manually.
   * Validates entity exists, validates asset belongs to entity (if provided),
   * normalises, exclusion-checks, deduplicates, and saves.
   */
  def submitManual(
      entityId: Long,
      assetId: Option[Long],
      rawValue: String
  ): ServiceResult[CandidateDiscovery]

  /**
   * Request permutation intake for an active domain asset.
   * Validates asset is active and domain-type, calls provider, stages all unique results.
   * Returns the count of newly staged discoveries.
   * Fails entirely on provider error — no partial batch written.
   *
   * @param entityId required because AssetReadPort resolves by entityId; the controller always has it.
   */
  def requestPermutation(entityId: Long, assetId: Long): ServiceResult[Int]

  /** Paginated list with optional status filter. */
  def listDiscoveries(
      entityId: Long,
      statusFilter: Option[DiscoveryStatusFilter],
      page: PageRequest
  ): Future[Page[CandidateDiscovery]]

  /** Single record lookup. */
  def getDiscovery(id: DiscoveryId): ServiceResult[CandidateDiscovery]
}
