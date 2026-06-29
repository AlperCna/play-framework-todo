package drp.discovery.application

/**
 * Query filter for `DiscoveryRepository.listByEntity`.
 *
 * SQL predicates per variant:
 *   PendingValidation → dns_status = 'pending' AND skip_reason IS NULL
 *   Whitelisted       → skip_reason = 'whitelisted'
 *   InvalidFormat     → skip_reason = 'invalid_format'
 *
 * None = no filter (show all records).
 */
sealed trait DiscoveryStatusFilter

object DiscoveryStatusFilter {
  case object PendingValidation extends DiscoveryStatusFilter
  case object Whitelisted       extends DiscoveryStatusFilter
  case object InvalidFormat     extends DiscoveryStatusFilter

  def fromQueryParam(s: String): Option[DiscoveryStatusFilter] = s match {
    case "pending"       => Some(PendingValidation)
    case "whitelisted"   => Some(Whitelisted)
    case "invalid_format" => Some(InvalidFormat)
    case _               => None
  }

  def toQueryParam(f: DiscoveryStatusFilter): String = f match {
    case PendingValidation => "pending"
    case Whitelisted       => "whitelisted"
    case InvalidFormat     => "invalid_format"
  }
}
