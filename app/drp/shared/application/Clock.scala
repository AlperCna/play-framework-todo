package drp.shared.application

import java.time.Instant

import javax.inject.Singleton

/** Time seam — inject instead of calling `Instant.now()` directly, so timestamps are testable. */
trait Clock {
  def now(): Instant
}

@Singleton
final class SystemClock extends Clock {
  def now(): Instant = Instant.now()
}
