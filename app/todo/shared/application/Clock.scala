package todo.shared.application

import java.time.{Instant, LocalDate}
import javax.inject.Singleton

trait Clock {
  def now: Instant
  def today: LocalDate
}

@Singleton
class SystemClock extends Clock {
  override def now: Instant = Instant.now()
  override def today: LocalDate = LocalDate.now()
}
