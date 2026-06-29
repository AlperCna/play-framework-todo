package drp.asset.domain

/**
 * Exclusion reason — an OPEN enum (no DB CHECK): known values are typed, an unrecognized value
 * round-trips as `Other(raw)` (FR-013).
 */
sealed trait ExclusionReason { def code: String }

object ExclusionReason {
  case object Manual extends ExclusionReason { val code = "manual" }
  case object OwnedUnmonitored extends ExclusionReason { val code = "owned_unmonitored" }
  case object ThirdPartyLegit extends ExclusionReason { val code = "third_party_legit" }
  final case class Other(raw: String) extends ExclusionReason { val code: String = raw }

  private val known: Seq[ExclusionReason] = Seq(Manual, OwnedUnmonitored, ThirdPartyLegit)
  val knownCodes: Seq[String] = known.map(_.code)

  def fromCode(raw: String): ExclusionReason = {
    val v = Option(raw).map(_.trim).getOrElse("")
    known.find(_.code.equalsIgnoreCase(v)).getOrElse(Other(v))
  }

  def toCode(r: ExclusionReason): String = r.code
}
