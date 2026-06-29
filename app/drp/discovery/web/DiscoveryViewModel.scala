package drp.discovery.web

import java.time.ZoneId
import java.time.format.DateTimeFormatter

import drp.discovery.domain.{CandidateDiscovery, DnsStatus, SkipReason}

/** Flat read model for Twirl — primitives only, no domain or persistence types. */
final case class DiscoveryViewModel(
    id: Long,
    entityId: Long,
    entityName: String,
    assetId: Option[Long],
    assetValue: Option[String],
    value: String,
    normalizedValue: String,
    source: String,
    dnsStatus: String,
    skipReason: Option[String],
    statusLabel: String,
    createdAt: String
)

object DiscoveryViewModel {

  private val displayTime =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

  def from(d: CandidateDiscovery, entityName: String, assetValue: Option[String]): DiscoveryViewModel =
    DiscoveryViewModel(
      id              = d.id.value,
      entityId        = d.entityId,
      entityName      = entityName,
      assetId         = d.assetId,
      assetValue      = assetValue,
      value           = d.value,
      normalizedValue = d.normalizedValue.value,
      source          = d.source.code,
      dnsStatus       = d.dnsStatus.code,
      skipReason      = d.skipReason.map(_.code),
      statusLabel     = resolveStatusLabel(d),
      createdAt       = displayTime.format(d.createdAt)
    )

  private def resolveStatusLabel(d: CandidateDiscovery): String =
    d.skipReason match {
      case Some(SkipReason.Whitelisted)   => "Whitelisted"
      case Some(SkipReason.InvalidFormat) => "Invalid Format"
      case None =>
        d.dnsStatus match {
          case DnsStatus.Pending  => "Pending Validation"
          case DnsStatus.Active   => "Active"
          case DnsStatus.Inactive => "Inactive"
          case DnsStatus.Error    => "Error"
        }
    }
}
