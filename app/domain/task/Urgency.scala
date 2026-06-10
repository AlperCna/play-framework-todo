package domain.task

import java.time.LocalDate

import domain.common.{DomainError, Priority}

/**
 * Oncelik + son tarih ikilisini TEK bir tipte birlestiren ADT.
 *
 * Is kurali (`High ⇒ dueDate zorunlu`) DOGRULAMAYLA degil, TIPLE garanti edilir:
 * `High` zorunlu bir `LocalDate` tasir, dolayisiyla "High ama tarihsiz" durumu
 * TEMSIL EDILEMEZ (make illegal states unrepresentable). Hicbir `copy`/yeni nesne
 * bu invariant'i kiramaz. `Low`/`Medium` icin son tarih opsiyoneldir.
 *
 * DOMAIN MODELI != DB MODELI: Bu fuzyon ADT'si duz iliskisel satira (priority INT,
 * due_date NULL) 1:1 OTURMAZ. Iliskisel bir DB eklenirse cevrim repository
 * sinirinda yapilir:
 *   - yazarken alanlara ayristirilir (`u.priority.value`, `u.dueDate`),
 *   - okurken `Urgency.from(...)` ile yeniden kurulur. Okuma `Either` dondurdugu
 *     icin "illegal satir" (High + NULL) bir veri-butunlugu hatasi olur; bunu ya
 *     bir DB CHECK constraint ile imkansiz kilar ya da repo sinirinda patlatirsin.
 */
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

  /** High HER ZAMAN bir son tarih tasir; tarihsiz kurulamaz (invariant tipte). */
  final case class High(date: LocalDate) extends Urgency {
    val priority: Priority = Priority.High
    def dueDate: Option[LocalDate] = Some(date)
  }

  /**
   * Ham `(priority, dueDate)` ikilisinden guvenli `Urgency` uretir; capraz-alan
   * invariant'inin TEK dogrulama noktasi. `High` secilip tarih verilmediyse
   * `HighPriorityRequiresDueDate`. Ciktisi artik TIPLI guvenli oldugundan, bu
   * noktadan sonra kuralin bir daha kontrol edilmesi GEREKMEZ.
   */
  def from(priority: Priority, dueDate: Option[LocalDate]): Either[DomainError, Urgency] =
    priority match {
      case Priority.Low    => Right(Low(dueDate))
      case Priority.Medium => Right(Medium(dueDate))
      case Priority.High   => dueDate.toRight(DomainError.HighPriorityRequiresDueDate).map(High(_))
    }
}
