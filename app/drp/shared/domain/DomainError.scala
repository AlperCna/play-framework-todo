package drp.shared.domain

sealed trait DomainError {
  def message: String
}

object DomainError {
  final case class EmptyField(field: String) extends DomainError {
    override val message: String = s"$field must not be empty"
  }

  final case class InvalidRange(field: String, min: BigDecimal, max: BigDecimal) extends DomainError {
    override val message: String = s"$field must be between $min and $max"
  }

  final case class InvalidHttpStatus(value: Int) extends DomainError {
    override val message: String = s"http status must be between 100 and 599: $value"
  }

  final case class InvalidStatusTransition(from: String, to: String) extends DomainError {
    override val message: String = s"invalid status transition from $from to $to"
  }

  final case class NegativeValue(field: String, value: Long) extends DomainError {
    override val message: String = s"$field must not be negative: $value"
  }

  final case class NotFound(entity: String, id: Long) extends DomainError {
    override val message: String = s"$entity not found: $id"
  }
}
