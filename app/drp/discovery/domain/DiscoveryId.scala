package drp.discovery.domain

/** Typed wrapper for the `candidate_discoveries.id` column. 0 = unsaved (DB assigns BIGSERIAL). */
final case class DiscoveryId(value: Long) extends AnyVal
