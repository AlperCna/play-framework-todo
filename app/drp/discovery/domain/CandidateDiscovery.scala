package drp.discovery.domain

import java.time.Instant

/**
 * Core aggregate for the discovery module. Created only through `CandidateDiscovery.intake`;
 * immutable after creation — only the DNS/HTTP validation step (a later story) updates it.
 */
final case class CandidateDiscovery(
    id: DiscoveryId,
    entityId: Long,
    assetId: Option[Long],
    value: String,
    normalizedValue: NormalizedValue,
    source: DiscoverySource,
    dnsStatus: DnsStatus,
    skipReason: Option[SkipReason],
    failedCheckCount: Int,
    httpStatusCode: Option[Int],
    lastCheckedAt: Option[Instant],
    nextCheckAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant
) {
  /** True when the record is eligible for DNS/HTTP validation (not skipped, not already checked). */
  def isValidationEligible: Boolean = skipReason.isEmpty && dnsStatus == DnsStatus.Pending
}

object CandidateDiscovery {

  /**
   * Smart constructor for the intake path. Produces a record with default intake state:
   * `dnsStatus = Pending`, `skipReason = None`, `failedCheckCount = 0`, all check fields `None`.
   * Exclusion logic is applied by the service BEFORE calling `intake`; it then calls `withSkipReason`
   * to attach the result.
   *
   * `id = DiscoveryId(0)` marks the record as unsaved; the repository assigns the real id.
   */
  def intake(
      entityId: Long,
      assetId: Option[Long],
      rawValue: String,
      source: DiscoverySource,
      now: Instant = Instant.now()
  ): CandidateDiscovery =
    CandidateDiscovery(
      id               = DiscoveryId(0),
      entityId         = entityId,
      assetId          = assetId,
      value            = rawValue,
      normalizedValue  = NormalizedValue.from(rawValue),
      source           = source,
      dnsStatus        = DnsStatus.Pending,
      skipReason       = None,
      failedCheckCount = 0,
      httpStatusCode   = None,
      lastCheckedAt    = None,
      nextCheckAt      = None,
      createdAt        = now,
      updatedAt        = now
    )
}
