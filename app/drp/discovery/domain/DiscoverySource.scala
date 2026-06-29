package drp.discovery.domain

sealed trait DiscoverySource { def code: String }

object DiscoverySource {
  case object Manual      extends DiscoverySource { val code = "manual"      }
  case object Permutation extends DiscoverySource { val code = "permutation" }

  def fromCode(code: String): Option[DiscoverySource] = code match {
    case "manual"      => Some(Manual)
    case "permutation" => Some(Permutation)
    case _             => None
  }

  def toCode(s: DiscoverySource): String = s.code
}
