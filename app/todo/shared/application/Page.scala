package todo.shared.application

final case class Page[+A](
    items: Seq[A],
    pageNumber: Int,
    pageSize: Int,
    totalCount: Long
) {

  val totalPages: Int =
    if (totalCount == 0) 0 else math.ceil(totalCount.toDouble / pageSize).toInt

  def hasPrev: Boolean = pageNumber > 1
  def hasNext: Boolean = pageNumber < totalPages
  def isEmpty: Boolean = items.isEmpty
  def nonEmpty: Boolean = items.nonEmpty

  def map[B](f: A => B): Page[B] = copy(items = items.map(f))
}

object Page {

  def from[A](items: Seq[A], request: PageRequest, totalCount: Long): Page[A] =
    Page(items, request.number.value, request.size.value, totalCount)

  def empty[A](request: PageRequest): Page[A] =
    Page(Nil, request.number.value, request.size.value, 0L)
}
