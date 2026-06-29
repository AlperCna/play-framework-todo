package drp.asset.domain

/**
 * Entity type — an OPEN enum (no DB CHECK on `entities.type`): known values are typed, an unrecognized
 * value round-trips as `Other(raw)` (FR-013). Persisted as its lowercase `code` string.
 */
sealed trait EntityType { def code: String }

object EntityType {
  case object Brand extends EntityType { val code = "brand" }
  case object Person extends EntityType { val code = "person" }
  case object Institution extends EntityType { val code = "institution" }
  final case class Other(raw: String) extends EntityType { val code: String = raw }

  private val known: Seq[EntityType] = Seq(Brand, Person, Institution)

  def fromCode(raw: String): EntityType = {
    val v = Option(raw).map(_.trim).getOrElse("")
    known.find(_.code.equalsIgnoreCase(v)).getOrElse(Other(v))
  }

  def toCode(t: EntityType): String = t.code
}
