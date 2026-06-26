package drp.discovery.domain

import java.time.Instant

import drp.shared.domain._

final case class DiscoverySource(value: String) extends AnyVal
object DiscoverySource {
  def create(value: String): Either[DomainError, DiscoverySource] =
    CommonValues.nonEmpty("discoverySource", value).map(DiscoverySource(_))
}

sealed trait DnsStatus { def value: String }
object DnsStatus {
  case object Pending  extends DnsStatus { val value = "pending" }
  case object Active   extends DnsStatus { val value = "active" }
  case object Inactive extends DnsStatus { val value = "inactive" }
  case object Error    extends DnsStatus { val value = "error" }
}

sealed trait SkipReason { def value: String }
object SkipReason {
  case object Whitelisted   extends SkipReason { val value = "whitelisted" }
  case object Duplicate     extends SkipReason { val value = "duplicate" }
  case object InvalidFormat extends SkipReason { val value = "invalid_format" }
}

final case class CandidateDiscovery(
    id: DiscoveryId,
    entityId: EntityId,
    assetId: Option[AssetId],
    value: String,
    normalizedValue: String,
    source: DiscoverySource,
    dnsStatus: DnsStatus,
    httpStatusCode: Option[Int],
    skipReason: Option[SkipReason],
    failedCheckCount: Int,
    lastCheckedAt: Option[Instant],
    nextCheckAt: Option[Instant],
    createdAt: Instant,
    updatedAt: Instant
) {
  def markActive(httpStatusCode: Int, checkedAt: Instant): Either[DomainError, CandidateDiscovery] =
    CandidateDiscovery.validateHttpStatus(httpStatusCode).map { _ =>
      copy(
        dnsStatus = DnsStatus.Active,
        httpStatusCode = Some(httpStatusCode),
        skipReason = None,
        lastCheckedAt = Some(checkedAt),
        nextCheckAt = None,
        updatedAt = checkedAt
      )
    }

  def markInactive(now: Instant, nextCheckAt: Instant): CandidateDiscovery =
    copy(
      dnsStatus = DnsStatus.Inactive,
      failedCheckCount = failedCheckCount + 1,
      lastCheckedAt = Some(now),
      nextCheckAt = Some(nextCheckAt),
      updatedAt = now
    )

  def markError(now: Instant, nextCheckAt: Instant): CandidateDiscovery =
    copy(
      dnsStatus = DnsStatus.Error,
      failedCheckCount = failedCheckCount + 1,
      lastCheckedAt = Some(now),
      nextCheckAt = Some(nextCheckAt),
      updatedAt = now
    )

  def skip(reason: SkipReason, now: Instant): CandidateDiscovery =
    copy(skipReason = Some(reason), updatedAt = now)

  def canPromote: Boolean =
    dnsStatus == DnsStatus.Active && skipReason.isEmpty
}

object CandidateDiscovery {
  def create(
      id: DiscoveryId,
      entityId: EntityId,
      assetId: Option[AssetId],
      value: String,
      normalizedValue: String,
      source: DiscoverySource,
      dnsStatus: DnsStatus,
      httpStatusCode: Option[Int],
      skipReason: Option[SkipReason],
      failedCheckCount: Int,
      lastCheckedAt: Option[Instant],
      nextCheckAt: Option[Instant],
      createdAt: Instant,
      updatedAt: Instant
  ): Either[DomainError, CandidateDiscovery] =
    for {
      validValue      <- CommonValues.nonEmpty("value", value)
      validNormalized <- CommonValues.nonEmpty("normalizedValue", normalizedValue)
      _               <- validateFailedCheckCount(failedCheckCount)
      _               <- httpStatusCode.fold[Either[DomainError, Unit]](Right(()))(validateHttpStatus)
    } yield CandidateDiscovery(
      id, entityId, assetId, validValue, validNormalized, source, dnsStatus,
      httpStatusCode, skipReason, failedCheckCount, lastCheckedAt, nextCheckAt, createdAt, updatedAt
    )

  def validateHttpStatus(value: Int): Either[DomainError, Unit] =
    if (value >= 100 && value <= 599) Right(()) else Left(DomainError.InvalidHttpStatus(value))

  private def validateFailedCheckCount(value: Int): Either[DomainError, Unit] =
    if (value >= 0) Right(()) else Left(DomainError.NegativeValue("failedCheckCount", value.toLong))
}
