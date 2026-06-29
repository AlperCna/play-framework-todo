package drp.discovery.application.ports

import scala.concurrent.Future

/**
 * Replaceable boundary for requesting look-alike domain variants.
 * The discovery module depends on this trait only — never on a concrete algorithm.
 * MVP implementation: FakePermutationProvider (deterministic, no external process).
 * Future implementation: DnstwistPermutationProvider (external process / API call).
 */
trait PermutationProvider {

  /**
   * Returns a batch of look-alike domain strings for the given asset hostname.
   * Each string is a raw, un-normalised candidate value.
   * Fails the Future on provider error — the caller MUST NOT write any partial batch.
   */
  def generateLookAlikes(assetValue: String): Future[Seq[String]]
}
