package drp.asset.domain

/**
 * Exclusion match type — a CLOSED enum (DB CHECK). Stored verbatim and NEVER evaluated in this feature;
 * the semantics belong to the future discovery module. `fromCode` returns `None` for an unknown value.
 */
sealed trait MatchType { def code: String }

object MatchType {
  case object Exact extends MatchType { val code = "exact" }
  case object RegistrableDomain extends MatchType { val code = "registrable_domain" }
  case object SubdomainOf extends MatchType { val code = "subdomain_of" }
  case object Pattern extends MatchType { val code = "pattern" }

  private val all: Seq[MatchType] = Seq(Exact, RegistrableDomain, SubdomainOf, Pattern)
  val codes: Seq[String] = all.map(_.code)

  def fromCode(raw: String): Option[MatchType] = {
    val v = Option(raw).map(_.trim.toLowerCase).getOrElse("")
    all.find(_.code == v)
  }

  def toCode(t: MatchType): String = t.code
}
