package drp.asset.domain

/**
 * Asset type — a CLOSED enum (DB `CHECK (asset_type IN ('domain','subdomain'))`): unknown values are
 * rejected (FR-013). `fromCode` returns `None` for an unrecognized value so callers reject it.
 */
sealed trait AssetType { def code: String }

object AssetType {
  case object Domain extends AssetType { val code = "domain" }
  case object Subdomain extends AssetType { val code = "subdomain" }

  private val all: Seq[AssetType] = Seq(Domain, Subdomain)
  val codes: Seq[String] = all.map(_.code)

  def fromCode(raw: String): Option[AssetType] = {
    val v = Option(raw).map(_.trim.toLowerCase).getOrElse("")
    all.find(_.code == v)
  }

  def toCode(t: AssetType): String = t.code
}
