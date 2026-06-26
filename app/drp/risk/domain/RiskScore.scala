package drp.risk.domain

import java.time.Instant

import drp.shared.domain._

sealed trait Verdict { def value: String }
object Verdict {
  case object Clean      extends Verdict { val value = "clean" }
  case object Suspicious extends Verdict { val value = "suspicious" }
  case object Malicious  extends Verdict { val value = "malicious" }
}

final case class TotalScore private (value: BigDecimal) extends AnyVal
object TotalScore {
  def create(value: BigDecimal): Either[DomainError, TotalScore] =
    Score.create("totalScore", value).map(s => TotalScore(s.value))
}

final case class Confidence private (value: BigDecimal) extends AnyVal
object Confidence {
  def create(value: BigDecimal): Either[DomainError, Confidence] =
    Score.create("confidence", value).map(s => Confidence(s.value))
}

final case class RuleSetVersion(value: String) extends AnyVal
object RuleSetVersion {
  def create(value: String): Either[DomainError, RuleSetVersion] =
    CommonValues.nonEmpty("ruleSetVersion", value).map(RuleSetVersion(_))
}

final case class RiskScore(
    id: RiskScoreId,
    candidateId: CandidateId,
    crawlResultId: Option[CrawlResultId],
    totalScore: TotalScore,
    verdict: Verdict,
    confidence: Option[Confidence],
    reasons: Metadata,
    llmSummary: Option[String],
    ruleSetVersion: RuleSetVersion,
    createdAt: Instant
)

object RiskScore {
  def verdictFor(score: TotalScore): Verdict = {
    val v = score.value
    if (v >= BigDecimal("0.70"))      Verdict.Malicious
    else if (v >= BigDecimal("0.40")) Verdict.Suspicious
    else                              Verdict.Clean
  }
}
