package drp.discovery.application

import com.google.common.net.InternetDomainName

import drp.asset.application.ports.ExclusionView
import drp.discovery.domain.{NormalizedValue, SkipReason}

/**
 * Pure matching logic for the four exclusion match types. No Guice injection — called directly
 * from `DiscoveryIntakeServiceImpl`.
 *
 * Match types:
 *   exact              → exact string equality between normalized discovery and normalized exclusion
 *   registrable_domain → same PSL registrable domain (e.g. `www.akbank.com` vs `akbank.com`)
 *   subdomain_of       → discovery host ends with `"." + exclusion host`; exclusion itself does NOT match
 *   pattern            → glob pattern (`*` → `.*`, `?` → `.`) converted to an anchored regex
 */
object ExclusionMatcher {

  def matches(discovery: NormalizedValue, exclusions: Seq[ExclusionView]): Option[SkipReason] =
    if (exclusions.exists(matchesExclusion(discovery.value, _))) Some(SkipReason.Whitelisted)
    else None

  private def matchesExclusion(discovery: String, excl: ExclusionView): Boolean = {
    excl.matchType match {
      case "exact" =>
        // Both sides must be canonical: normalize the stored exclusion value the same way discovery was normalized.
        discovery == NormalizedValue.from(excl.value).value
      case "registrable_domain" =>
        // InternetDomainName.from canonicalises to lowercase internally — no extra step needed.
        sameRegistrableDomain(discovery, excl.value)
      case "subdomain_of" =>
        // Normalize the exclusion host so casing / trailing dots don't cause misses.
        discovery.endsWith("." + NormalizedValue.from(excl.value).value)
      case "pattern" =>
        // Glob patterns may contain wildcards (*?) that cannot be hostname-parsed; lowercase is safe.
        globMatch(excl.value.toLowerCase(java.util.Locale.ROOT), discovery)
      case _ => false
    }
  }

  private def sameRegistrableDomain(a: String, b: String): Boolean =
    try {
      val da = InternetDomainName.from(a)
      val db = InternetDomainName.from(b)
      if (da.isUnderPublicSuffix && db.isUnderPublicSuffix)
        da.topPrivateDomain().toString == db.topPrivateDomain().toString
      else
        a == b
    } catch { case _: Exception => false }

  private def globMatch(pattern: String, value: String): Boolean = {
    val regex = "\\Q" + pattern.replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "\\E"
    value.matches(regex)
  }
}
