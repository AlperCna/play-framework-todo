package todo.shared.domain

sealed trait Priority {
  def value: Int
}

object Priority {

  case object Low extends Priority { val value = 0 }
  case object Medium extends Priority { val value = 1 }
  case object High extends Priority { val value = 2 }

  val all: Seq[Priority] = Seq(Low, Medium, High)

  def fromInt(i: Int): Option[Priority] = all.find(_.value == i)

  implicit val ordering: Ordering[Priority] = Ordering.by(_.value)
}
