package drp.asset.domain

/**
 * Closed set of exclusion match strategies, backed by the DB CHECK `ck_exclusions_match_type`.
 * `fromValue` rejects any string outside the four allowed values.
 */
sealed trait ExclusionMatchType {
  def asValue: String
}

object ExclusionMatchType {

  case object Exact extends ExclusionMatchType { val asValue = "exact" }
  case object RegistrableDomain extends ExclusionMatchType { val asValue = "registrable_domain" }
  case object SubdomainOf extends ExclusionMatchType { val asValue = "subdomain_of" }
  case object Pattern extends ExclusionMatchType { val asValue = "pattern" }

  val all: Seq[ExclusionMatchType] = Seq(Exact, RegistrableDomain, SubdomainOf, Pattern)

  def fromValue(value: String): Option[ExclusionMatchType] = {
    val normalized = Option(value).map(_.trim).getOrElse("")
    all.find(_.asValue == normalized)
  }
}
