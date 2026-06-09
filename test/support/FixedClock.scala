package support

import java.time.{Instant, LocalDate}

import services.Clock

/** Testlerde deterministik zaman icin sabit saat. */
final case class FixedClock(now: Instant, today: LocalDate) extends Clock
