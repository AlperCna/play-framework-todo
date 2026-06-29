package drp.discovery.infrastructure.tables

import java.time.Instant

import drp.discovery.domain._
import drp.shared.infrastructure.MonaPgProfile
import MonaPgProfile.api._

/** Infrastructure-only row projection — never crosses the module boundary. */
private[discovery] final case class DiscoveryRow(
    id: Long,
    entityId: Long,
    assetId: Option[Long],
    value: String,
    normalizedValue: String,
    source: String,
    dnsStatus: String,
    skipReason: Option[String],
    failedCheckCount: Int,
    httpStatusCode: Option[Int],
    lastCheckedAt: Option[Instant],
    nextCheckAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant
)

/**
 * Slick table definition for `candidate_discoveries` on `MonaPgProfile`.
 * Sealed-type columns (`source`, `dns_status`, `skip_reason`) are stored as TEXT and mapped via their
 * `code` strings; the sealed ADT values are instantiated in `toDomain`.
 */
private[discovery] trait CandidateDiscoveriesTable {

  class DiscoveriesTableDef(tag: Tag) extends Table[DiscoveryRow](tag, "candidate_discoveries") {
    def id               = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def entityId         = column[Long]("entity_id")
    def assetId          = column[Option[Long]]("asset_id")
    def value            = column[String]("value")
    def normalizedValue  = column[String]("normalized_value")
    def source           = column[String]("source")
    def dnsStatus        = column[String]("dns_status")
    def skipReason       = column[Option[String]]("skip_reason")
    def failedCheckCount = column[Int]("failed_check_count")
    def httpStatusCode   = column[Option[Int]]("http_status_code")
    def lastCheckedAt    = column[Option[Instant]]("last_checked_at")
    def nextCheckAt      = column[Option[Instant]]("next_check_at")
    def createdAt        = column[Instant]("created_at")
    def updatedAt        = column[Instant]("updated_at")

    def * = (
      id, entityId, assetId, value, normalizedValue, source, dnsStatus,
      skipReason, failedCheckCount, httpStatusCode, lastCheckedAt, nextCheckAt,
      createdAt, updatedAt
    ).mapTo[DiscoveryRow]
  }

  val candidateDiscoveries = TableQuery[DiscoveriesTableDef]

  protected def toDomain(r: DiscoveryRow): CandidateDiscovery =
    CandidateDiscovery(
      id               = DiscoveryId(r.id),
      entityId         = r.entityId,
      assetId          = r.assetId,
      value            = r.value,
      normalizedValue  = NormalizedValue(r.normalizedValue),
      source           = DiscoverySource.fromCode(r.source).getOrElse(DiscoverySource.Permutation),
      dnsStatus        = DnsStatus.fromCode(r.dnsStatus).getOrElse(DnsStatus.Pending),
      skipReason       = r.skipReason.flatMap(SkipReason.fromCode),
      failedCheckCount = r.failedCheckCount,
      httpStatusCode   = r.httpStatusCode,
      lastCheckedAt    = r.lastCheckedAt,
      nextCheckAt      = r.nextCheckAt,
      createdAt        = r.createdAt,
      updatedAt        = r.updatedAt
    )
}
