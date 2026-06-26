package drp.asset.domain

import java.time.Instant

import drp.shared.domain._

sealed trait ExclusionMatchType { def value: String }
object ExclusionMatchType {
  case object Exact             extends ExclusionMatchType { val value = "exact" }
  case object RegistrableDomain extends ExclusionMatchType { val value = "registrable_domain" }
  case object SubdomainOf       extends ExclusionMatchType { val value = "subdomain_of" }
  case object Pattern           extends ExclusionMatchType { val value = "pattern" }
}

final case class ExclusionReason(value: String) extends AnyVal
object ExclusionReason {
  def create(value: String): Either[DomainError, ExclusionReason] =
    CommonValues.nonEmpty("exclusionReason", value).map(ExclusionReason(_))
}

final case class Exclusion(
    id: ExclusionId,
    entityId: Option[EntityId],
    value: String,
    matchType: ExclusionMatchType,
    reason: ExclusionReason,
    isActive: Boolean,
    createdBy: String,
    createdAt: Instant,
    updatedAt: Instant
) {
  def deactivate(updatedAt: Instant): Exclusion =
    copy(isActive = false, updatedAt = updatedAt)
}

object Exclusion {
  def create(
      id: ExclusionId,
      entityId: Option[EntityId],
      value: String,
      matchType: ExclusionMatchType,
      reason: ExclusionReason,
      isActive: Boolean,
      createdBy: String,
      createdAt: Instant,
      updatedAt: Instant
  ): Either[DomainError, Exclusion] =
    for {
      validValue     <- CommonValues.nonEmpty("value", value)
      validCreatedBy <- CommonValues.nonEmpty("createdBy", createdBy)
    } yield Exclusion(id, entityId, validValue, matchType, reason, isActive, validCreatedBy, createdAt, updatedAt)
}
