package todo.shared.domain

sealed trait DomainError {
  def code: String
  def message: String
}

object DomainError {

  case object EmptyTitle extends DomainError {
    val code = "error.domain.emptyTitle"
    val message = "Title cannot be empty."
  }
  case object EmptyEmail extends DomainError {
    val code = "error.domain.emptyEmail"
    val message = "Email cannot be empty."
  }
  case object EmptyPassword extends DomainError {
    val code = "error.domain.emptyPassword"
    val message = "Password cannot be empty."
  }
  case object EmptyCategoryName extends DomainError {
    val code = "error.domain.emptyCategoryName"
    val message = "Category name cannot be empty."
  }
  case object EmptyCategoryDescription extends DomainError {
    val code = "error.domain.emptyCategoryDescription"
    val message = "Category description cannot be empty."
  }

  case object InvalidUserId extends DomainError {
    val code = "error.domain.invalidUserId"
    val message = "User id must be greater than zero."
  }
  case object InvalidTaskItemId extends DomainError {
    val code = "error.domain.invalidTaskItemId"
    val message = "Task id must be greater than zero."
  }
  case object InvalidCategoryId extends DomainError {
    val code = "error.domain.invalidCategoryId"
    val message = "Category id must be greater than zero."
  }

  case object HighPriorityRequiresDueDate extends DomainError {
    val code = "error.domain.highPriorityRequiresDueDate"
    val message = "High priority tasks require a due date."
  }
  case object TaskPastDueCannotComplete extends DomainError {
    val code = "error.domain.taskPastDueCannotComplete"
    val message = "A task past its due date cannot be completed."
  }
  case object CategoryDeleted extends DomainError {
    val code = "error.domain.categoryDeleted"
    val message = "A task cannot be assigned to a deleted category."
  }

  case object EmailAlreadyTaken extends DomainError {
    val code = "error.domain.emailAlreadyTaken"
    val message = "This email is already registered."
  }
  case object InvalidCredentials extends DomainError {
    val code = "error.domain.invalidCredentials"
    val message = "Invalid email or password."
  }

  final case class InvalidPriorityValue(raw: Int) extends DomainError {
    val code = "error.domain.invalidPriorityValue"
    val message = s"'$raw' is not a valid priority value."
  }

  final case class NotFound(entity: String, id: Long) extends DomainError {
    val code = "error.domain.notFound"
    val message = s"$entity with id $id was not found."
  }
}
