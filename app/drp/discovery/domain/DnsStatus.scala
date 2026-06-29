package drp.discovery.domain

sealed trait DnsStatus { def code: String }

object DnsStatus {
  /** Default status on intake — DNS/HTTP check not yet performed. */
  case object Pending  extends DnsStatus { val code = "pending"  }
  case object Active   extends DnsStatus { val code = "active"   }
  case object Inactive extends DnsStatus { val code = "inactive" }
  case object Error    extends DnsStatus { val code = "error"    }

  def fromCode(code: String): Option[DnsStatus] = code match {
    case "pending"  => Some(Pending)
    case "active"   => Some(Active)
    case "inactive" => Some(Inactive)
    case "error"    => Some(Error)
    case _          => None
  }

  def toCode(s: DnsStatus): String = s.code
}
