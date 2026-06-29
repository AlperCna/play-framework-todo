package drp.discovery.domain

import java.net.{IDN, URI}
import java.util.Locale

/**
 * Canonical hostname derived from a raw user-submitted string.
 *
 * Rules applied by `NormalizedValue.from`:
 * 1. Trim surrounding whitespace.
 * 2. Extract the hostname via `java.net.URI`. If `URI.getHost` is null (bare hostname without scheme),
 *    prepend `https://` and retry.
 * 3. Convert IDN to Punycode via `java.net.IDN.toASCII(ALLOW_UNASSIGNED)`.
 * 4. Lowercase.
 * 5. Remove a trailing dot (FQDN notation).
 * 6. `www` label is preserved — never stripped.
 * 7. Scheme, credentials, port, path, query, and fragment are discarded.
 * 8. Malformed non-empty input that cannot be parsed →
 *    `"invalid:" + trimmed.toLowerCase.replaceAll("\\s+", " ")`.
 *
 * `from` never throws.
 */
final case class NormalizedValue(value: String) extends AnyVal

object NormalizedValue {

  def from(raw: String): NormalizedValue = {
    val trimmed = raw.trim
    if (trimmed.isEmpty) NormalizedValue("invalid:")
    else NormalizedValue(normalize(trimmed))
  }

  private def normalize(trimmed: String): String =
    extractHost(trimmed) match {
      case Some(host) =>
        val ascii = try IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) catch { case _: Exception => host }
        val lower = ascii.toLowerCase(Locale.ROOT)
        if (lower.endsWith(".")) lower.dropRight(1) else lower
      case None =>
        "invalid:" + trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
    }

  private def extractHost(s: String): Option[String] = {
    // First attempt: parse as-is (handles `https://host/path` etc.)
    hostOf(s).orElse {
      // Second attempt: treat as bare hostname by prepending scheme
      hostOf("https://" + s)
    }
  }

  private def hostOf(s: String): Option[String] =
    try {
      val uri = new URI(s)
      Option(uri.getHost).filter(_.nonEmpty)
    } catch {
      case _: Exception => None
    }
}
