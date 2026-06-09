package domain.common

/**
 * Bir is kurali (invariant) ihlal edildiginde uretilen, domain'e ozgu hata tipi.
 *
 * DOMAIN-SPEC bunu `DomainException` olarak tarif eder; Scala tarafinda
 * idiomatic secim istisna FIRLATMAK yerine `Either[DomainError, T]` ile tipli
 * ve anlamli bir deger DONDURMEKTIR. Boylece hata, derleyici tarafindan gorulen
 * normal bir veri akisi olur.
 *
 * Her hata:
 *   - `code`: i18n anahtari (view/flash tarafinda `messages(code)` ile cevrilir).
 *   - `message`: i18n anahtari bulunamazsa kullanilacak Ingilizce yedek metin.
 */
sealed trait DomainError {
  def code: String
  def message: String
}

object DomainError {

  // --- Bos/zorunlu alan ihlalleri ---
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

  // --- Gecersiz kimlik/FK ihlalleri (tum FK > 0 olmali) ---
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

  // --- TaskItem is kurallari ---
  case object HighPriorityRequiresDueDate extends DomainError {
    val code = "error.domain.highPriorityRequiresDueDate"
    val message = "High priority tasks require a due date."
  }
  case object TaskPastDueCannotComplete extends DomainError {
    val code = "error.domain.taskPastDueCannotComplete"
    val message = "A task past its due date cannot be completed."
  }
  case object CannotClearDueDateForHighPriority extends DomainError {
    val code = "error.domain.cannotClearDueDateForHighPriority"
    val message = "Due date cannot be cleared while priority is High."
  }
  case object CategoryDeleted extends DomainError {
    val code = "error.domain.categoryDeleted"
    val message = "A task cannot be assigned to a deleted category."
  }

  /** Form'dan gelen sayinin gecerli bir Priority'ye karsilik gelmemesi. */
  final case class InvalidPriorityValue(raw: Int) extends DomainError {
    val code = "error.domain.invalidPriorityValue"
    val message = s"'$raw' is not a valid priority value."
  }

  /** Servis seviyesinde: istenen kayit bulunamadi. */
  final case class NotFound(entity: String, id: Long) extends DomainError {
    val code = "error.domain.notFound"
    val message = s"$entity with id $id was not found."
  }
}
