package drp.candidate.domain

import java.time.Instant

import drp.shared.domain._

final case class CandidateSource(value: String) extends AnyVal
object CandidateSource {
  def create(value: String): Either[DomainError, CandidateSource] =
    CommonValues.nonEmpty("candidateSource", value).map(CandidateSource(_))
}

sealed trait CandidateStatus { def value: String }
object CandidateStatus {
  case object Validated  extends CandidateStatus { val value = "validated" }
  case object Crawled    extends CandidateStatus { val value = "crawled" }
  case object Analyzed   extends CandidateStatus { val value = "analyzed" }
  case object Scored     extends CandidateStatus { val value = "scored" }
  case object Reviewed   extends CandidateStatus { val value = "reviewed" }
  case object Closed     extends CandidateStatus { val value = "closed" }
  case object Eliminated extends CandidateStatus { val value = "eliminated" }
  case object Error      extends CandidateStatus { val value = "error" }

  val happyPathTransitions: Map[CandidateStatus, CandidateStatus] = Map(
    Validated -> Crawled,
    Crawled   -> Analyzed,
    Analyzed  -> Scored,
    Scored    -> Reviewed,
    Reviewed  -> Closed
  )
}

final case class Candidate(
    id: CandidateId,
    entityId: EntityId,
    discoveryId: DiscoveryId,
    source: CandidateSource,
    value: String,
    normalizedValue: String,
    status: CandidateStatus,
    metadata: Metadata,
    discoveredAt: Instant,
    createdAt: Instant,
    updatedAt: Instant
) {
  def advanceTo(nextStatus: CandidateStatus, now: Instant): Either[DomainError, Candidate] =
    if (CandidateStatus.happyPathTransitions.get(status).contains(nextStatus))
      Right(copy(status = nextStatus, updatedAt = now))
    else
      Left(DomainError.InvalidStatusTransition(status.value, nextStatus.value))

  def eliminate(now: Instant): Candidate = copy(status = CandidateStatus.Eliminated, updatedAt = now)
  def markError(now: Instant): Candidate = copy(status = CandidateStatus.Error, updatedAt = now)
}

object Candidate {
  def create(
      id: CandidateId,
      entityId: EntityId,
      discoveryId: DiscoveryId,
      source: CandidateSource,
      value: String,
      normalizedValue: String,
      status: CandidateStatus,
      metadata: Metadata,
      discoveredAt: Instant,
      createdAt: Instant,
      updatedAt: Instant
  ): Either[DomainError, Candidate] =
    for {
      validValue      <- CommonValues.nonEmpty("value", value)
      validNormalized <- CommonValues.nonEmpty("normalizedValue", normalizedValue)
    } yield Candidate(id, entityId, discoveryId, source, validValue, validNormalized, status, metadata, discoveredAt, createdAt, updatedAt)
}
