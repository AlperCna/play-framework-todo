package domain.auth

/**
 * Basit domain sonuc modeli (DOMAIN-SPEC bolum 8).
 *
 * Parola artik plain text oldugundan dogrulama = duz karsilastirma. Bu tip,
 * spec butunlugu icin tanimlanir; bu fazda bir UI'a baglanmaz.
 */
sealed trait AuthenticationResult

object AuthenticationResult {
  case object Success extends AuthenticationResult
  final case class Failure(message: String) extends AuthenticationResult
}
