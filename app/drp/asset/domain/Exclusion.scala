package drp.asset.domain

import java.time.Instant

import drp.shared.domain.DomainError

/** Strongly-typed exclusion id (0 = unsaved). */
final case class ExclusionId(value: Long) extends AnyVal

/**
 * An allowlist entry under an entity. Always entity-scoped in this feature (FR-008); the `value` is
 * stored verbatim and NEVER evaluated/matched here. `createdBy` is the constant "system" (no auth yet).
 */
final case class Exclusion(
    id: ExclusionId,
    entityId: EntityId,
    value: String,
    matchType: MatchType,
    reason: ExclusionReason,
    isActive: Boolean,
    createdBy: String,
    createdAt: Instant,
    updatedAt: Instant
) {

  def edit(newValue: String, newMatchType: String, newReason: String, now: Instant): Either[DomainError, Exclusion] =
    for {
      v  <- Exclusion.requireNonBlank(newValue)
      mt <- MatchType.fromCode(newMatchType).toRight(DomainError.InvalidMatchType(newMatchType))
    } yield copy(value = v, matchType = mt, reason = ExclusionReason.fromCode(newReason), updatedAt = now)
}

object Exclusion {

  val CreatedBySystem = "system"

  /** Smart ctor: entity-scoped; blank value → `EmptyExclusionValue`; unknown match type → `InvalidMatchType`. */
  def create(entityId: EntityId, value: String, matchType: String, reason: String, now: Instant): Either[DomainError, Exclusion] =
    for {
      v  <- requireNonBlank(value)
      mt <- MatchType.fromCode(matchType).toRight(DomainError.InvalidMatchType(matchType))
    } yield Exclusion(ExclusionId(0L), entityId, v, mt, ExclusionReason.fromCode(reason), isActive = true, CreatedBySystem, now, now)

  private def requireNonBlank(s: String): Either[DomainError, String] =
    if (s == null || s.trim.isEmpty) Left(DomainError.EmptyExclusionValue) else Right(s.trim)
}
