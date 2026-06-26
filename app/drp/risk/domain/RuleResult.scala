package drp.risk.domain

import java.time.Instant

import drp.shared.domain._

final case class RuleWeight private (value: BigDecimal) extends AnyVal
object RuleWeight {
  def create(value: BigDecimal): Either[DomainError, RuleWeight] =
    Score.create("ruleWeight", value).map(s => RuleWeight(s.value))
}

final case class RuleCode(value: String) extends AnyVal
object RuleCode {
  def create(value: String): Either[DomainError, RuleCode] =
    CommonValues.nonEmpty("ruleCode", value).map(RuleCode(_))
}

final case class RuleResult(
    id: RuleResultId,
    riskScoreId: RiskScoreId,
    ruleCode: RuleCode,
    weight: RuleWeight,
    detail: Option[Metadata],
    createdAt: Instant
)
