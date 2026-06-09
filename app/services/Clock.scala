package services

import java.time.{Instant, LocalDate}
import javax.inject.Singleton

/**
 * Zaman portu.
 *
 * Domain saf kalsin diye `Instant.now()` / `LocalDate.now()` cagrilari domain'e
 * sizmaz; bu yan etkiyi servis katmani bu port uzerinden alir. Testlerde sabit
 * saatli bir implementasyon ([[services.FixedClock]] gibi) verilerek davranis
 * deterministik kilinir.
 */
trait Clock {
  def now: Instant
  def today: LocalDate
}

/** Gercek sistem saatini kullanan implementasyon (uretim). */
@Singleton
class SystemClock extends Clock {
  override def now: Instant = Instant.now()
  override def today: LocalDate = LocalDate.now()
}
