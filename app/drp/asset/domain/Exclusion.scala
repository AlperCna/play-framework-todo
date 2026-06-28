package drp.asset.domain

import java.time.Instant

/**
 * An allowlist entry (owned-but-unmonitored or legitimate third-party domain/URL) under a protected
 * entity. The future discovery pipeline consults exclusions before flagging a candidate. `value` is
 * stored as entered (no domain normalization); `matchType` is a closed set; `reason` is open.
 */
final case class Exclusion(
    id: Long,
    entityId: Long,
    value: String,
    matchType: ExclusionMatchType,
    reason: String,
    isActive: Boolean,
    createdBy: String,
    createdAt: Instant,
    updatedAt: Instant
)

object Exclusion {

  /**
   * Smart constructor for a NEW exclusion. `id` and timestamps are placeholders (DB-assigned);
   * `isActive`/`createdBy` reflect the DB defaults (true / 'system'). Validates non-blank `value`
   * (FR-002), non-blank `reason` (FR-002), and a `matchType` within the closed set (FR-004).
   */
  def create(
      entityId: Long,
      value: String,
      matchType: String,
      reason: String
  ): Either[AssetDomainError, Exclusion] = {
    val trimmedValue  = Option(value).map(_.trim).getOrElse("")
    val trimmedReason = Option(reason).map(_.trim).getOrElse("")
    if (trimmedValue.isEmpty) Left(AssetDomainError.BlankExclusionValue)
    else if (trimmedReason.isEmpty) Left(AssetDomainError.BlankExclusionReason)
    else
      ExclusionMatchType.fromValue(matchType) match {
        case None =>
          Left(AssetDomainError.InvalidMatchType(Option(matchType).map(_.trim).getOrElse("")))
        case Some(mt) =>
          Right(
            Exclusion(
              0L,
              entityId,
              trimmedValue,
              mt,
              trimmedReason,
              isActive = true,
              createdBy = "system",
              Instant.EPOCH,
              Instant.EPOCH
            )
          )
      }
  }
}
