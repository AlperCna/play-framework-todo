package drp.shared.application

/** One page of results plus the paging context a view needs to render navigation. */
final case class Page[A](items: Seq[A], page: Int, size: Int, total: Long) {
  def totalPages: Int = if (size <= 0) 0 else math.ceil(total.toDouble / size).toInt
  def hasPrev: Boolean = page > 1
  def hasNext: Boolean = page < totalPages

  /** Map the items to a view type while keeping the paging context. */
  def map[B](f: A => B): Page[B] = Page(items.map(f), page, size, total)
}
