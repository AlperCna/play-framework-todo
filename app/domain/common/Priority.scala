package domain.common

/**
 * Gorev onceligi. Sabit kume, siralanabilir.
 *
 * Scala 2.13 kullandigimiz icin (Scala 3'un `enum`'u yok) idiomatic karsilik
 * `sealed trait` + `case object`'lerdir. Sayisal karsiliklar (0/1/2) DB ve
 * serialization icin korunur.
 */
sealed trait Priority {

  /** DB/form/serialization icin sayisal karsilik. */
  def value: Int
}

object Priority {

  case object Low extends Priority { val value = 0 }
  case object Medium extends Priority { val value = 1 }
  case object High extends Priority { val value = 2 }

  /** Tum degerler, sayisal sira ile. */
  val all: Seq[Priority] = Seq(Low, Medium, High)

  /** Sayidan Priority'ye; gecersiz sayida None. */
  def fromInt(i: Int): Option[Priority] = all.find(_.value == i)

  /** Priority'ler sayisal degerlerine gore siralanir (Low < Medium < High). */
  implicit val ordering: Ordering[Priority] = Ordering.by(_.value)
}
