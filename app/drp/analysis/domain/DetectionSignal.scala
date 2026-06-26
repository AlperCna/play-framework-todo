package drp.analysis.domain

import java.time.Instant

import drp.shared.domain._

final case class SignalType(value: String) extends AnyVal
object SignalType {
  def create(value: String): Either[DomainError, SignalType] =
    CommonValues.nonEmpty("signalType", value).map(SignalType(_))
}

final case class SignalScore private (value: BigDecimal) extends AnyVal
object SignalScore {
  def create(value: BigDecimal): Either[DomainError, SignalScore] =
    Score.create("signalScore", value).map(s => SignalScore(s.value))
}

final case class DetectionSignal(
    id: DetectionSignalId,
    candidateId: CandidateId,
    crawlResultId: Option[CrawlResultId],
    signalType: SignalType,
    score: SignalScore,
    details: Metadata,
    metadata: Metadata,
    createdAt: Instant
)
