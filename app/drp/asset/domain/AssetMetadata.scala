package drp.asset.domain

/**
 * Reference-only metadata for an asset (no binary content). Assembled by the system from discrete
 * form fields (FR-016) and stored as JSONB. All references are optional.
 */
final case class AssetMetadata(
    homepageUrl: Option[String],
    loginPageUrl: Option[String],
    logoRef: Option[String],
    faviconRef: Option[String]
)

object AssetMetadata {
  val empty: AssetMetadata = AssetMetadata(None, None, None, None)

  /** Build from raw form strings — blank/whitespace becomes `None`. */
  def of(homepage: String, login: String, logo: String, favicon: String): AssetMetadata = {
    def opt(s: String): Option[String] = Option(s).map(_.trim).filter(_.nonEmpty)
    AssetMetadata(opt(homepage), opt(login), opt(logo), opt(favicon))
  }
}
