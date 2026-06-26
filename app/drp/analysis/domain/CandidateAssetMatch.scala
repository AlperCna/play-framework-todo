package drp.analysis.domain

import java.time.Instant

import drp.shared.domain._

final case class MatchType(value: String) extends AnyVal
object MatchType {
  def create(value: String): Either[DomainError, MatchType] =
    CommonValues.nonEmpty("matchType", value).map(MatchType(_))
}

final case class SimilarityScore private (value: BigDecimal) extends AnyVal
object SimilarityScore {
  def create(value: BigDecimal): Either[DomainError, SimilarityScore] =
    Score.create("similarityScore", value).map(s => SimilarityScore(s.value))
}

final case class CandidateAssetMatch(
    id: CandidateAssetMatchId,
    candidateId: CandidateId,
    assetId: AssetId,
    crawlResultId: Option[CrawlResultId],
    matchType: MatchType,
    similarityScore: SimilarityScore,
    details: Metadata,
    createdAt: Instant
)
