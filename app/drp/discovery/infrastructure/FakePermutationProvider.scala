package drp.discovery.infrastructure

import scala.concurrent.Future

import drp.discovery.application.ports.PermutationProvider

/**
 * Deterministic test double — returns the seed unchanged. Registered via `toInstance` in
 * DiscoveryModule when `drp.inMemory = true` or in Test mode.
 */
class FakePermutationProvider(seed: Seq[String]) extends PermutationProvider {
  override def generateLookAlikes(assetValue: String): Future[Seq[String]] =
    Future.successful(seed)
}

object FakePermutationProvider {
  /**
   * Default seed used by DiscoveryModule. Covers the permutation intake test scenarios:
   * - a valid domain that is new
   * - a valid domain that duplicates an already-staged value (to verify dedup)
   * - a domain that would match a standard exclusion for the protected asset
   * - a malformed value (to verify normalisation → invalid_format path)
   * - an empty string (to verify blank-filtering)
   */
  val defaultSeed: Seq[String] = Seq(
    "akbank-guvenli-odeme.com",
    "akbank-guvenli-giris.com",  // duplicates the canonical example from quickstart
    "akbank.com",                // matches the typical exact exclusion
    "not a domain !! @@",        // normalises to invalid_format
    ""                           // blank — filtered before normalisation
  )
}
