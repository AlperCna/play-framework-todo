package todo.task.domain

import java.time.LocalDate

import todo.shared.domain.DomainError
import todo.shared.domain.Priority

sealed trait Urgency {
  def priority: Priority
  def dueDate: Option[LocalDate]
}

object Urgency {

  final case class Low(dueDate: Option[LocalDate]) extends Urgency {
    val priority: Priority = Priority.Low
  }

  final case class Medium(dueDate: Option[LocalDate]) extends Urgency {
    val priority: Priority = Priority.Medium
  }

  final case class High(date: LocalDate) extends Urgency {
    val priority: Priority = Priority.High
    def dueDate: Option[LocalDate] = Some(date)
  }

  def from(priority: Priority, dueDate: Option[LocalDate]): Either[DomainError, Urgency] =
    priority match {
      case Priority.Low    => Right(Low(dueDate))
      case Priority.Medium => Right(Medium(dueDate))
      case Priority.High   => dueDate.toRight(DomainError.HighPriorityRequiresDueDate).map(High(_))
    }
}
