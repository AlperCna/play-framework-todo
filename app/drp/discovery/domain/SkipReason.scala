package drp.discovery.domain

sealed trait SkipReason { def code: String }

object SkipReason {
  case object Whitelisted   extends SkipReason { val code = "whitelisted"   }
  case object InvalidFormat extends SkipReason { val code = "invalid_format" }

  def fromCode(code: String): Option[SkipReason] = code match {
    case "whitelisted"   => Some(Whitelisted)
    case "invalid_format" => Some(InvalidFormat)
    case "duplicate"     => None  // defined in DB CHECK but not used by this story
    case _               => None
  }

  def toCode(s: SkipReason): String = s.code
}
