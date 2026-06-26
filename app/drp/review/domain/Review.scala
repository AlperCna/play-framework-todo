package drp.review.domain

import java.time.Instant

import drp.shared.domain._

sealed trait ReviewDecision { def value: String }
object ReviewDecision {
  case object Confirmed     extends ReviewDecision { val value = "confirmed" }
  case object FalsePositive extends ReviewDecision { val value = "false_positive" }
  case object NeedsMoreInfo extends ReviewDecision { val value = "needs_more_info" }
}

final case class Reviewer(value: String) extends AnyVal
object Reviewer {
  def create(value: String): Either[DomainError, Reviewer] =
    CommonValues.nonEmpty("reviewer", value).map(Reviewer(_))
}

final case class Review(
    id: ReviewId,
    candidateId: CandidateId,
    riskScoreId: RiskScoreId,
    reviewer: Reviewer,
    decision: ReviewDecision,
    notes: Option[String],
    reviewedAt: Instant,
    createdAt: Instant
)
