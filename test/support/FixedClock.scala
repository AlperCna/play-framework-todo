package support

import java.time.{Instant, LocalDate}

import todo.shared.application.Clock

final case class FixedClock(now: Instant, today: LocalDate) extends Clock
