package drp.casework.domain

import java.time.Instant

import drp.shared.domain._

sealed trait CaseStatus { def value: String }
object CaseStatus {
  case object Open               extends CaseStatus { val value = "open" }
  case object TakedownRequested  extends CaseStatus { val value = "takedown_requested" }
  case object Closed             extends CaseStatus { val value = "closed" }
  case object FalsePositive      extends CaseStatus { val value = "false_positive" }
}

sealed trait CasePriority { def value: String }
object CasePriority {
  case object Low    extends CasePriority { val value = "low" }
  case object Medium extends CasePriority { val value = "medium" }
  case object High   extends CasePriority { val value = "high" }
}

final case class Case(
    id: CaseId,
    candidateId: CandidateId,
    reviewId: ReviewId,
    status: CaseStatus,
    priority: Option[CasePriority],
    takedownSentAt: Option[Instant],
    notes: Option[String],
    createdAt: Instant,
    updatedAt: Instant
) {
  def requestTakedown(now: Instant): Case = copy(status = CaseStatus.TakedownRequested, takedownSentAt = Some(now), updatedAt = now)
  def close(now: Instant): Case           = copy(status = CaseStatus.Closed, updatedAt = now)
  def markFalsePositive(now: Instant): Case = copy(status = CaseStatus.FalsePositive, updatedAt = now)
}
