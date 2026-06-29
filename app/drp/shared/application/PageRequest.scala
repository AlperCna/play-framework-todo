package drp.shared.application

/** A 1-based page request with a bounded page size (DRP list reads default to pagination — Constitution IV). */
final case class PageRequest(page: Int, size: Int) {
  /** Zero-based row offset for the underlying query. */
  def offset: Int = (math.max(page, 1) - 1) * size
}

object PageRequest {
  val DefaultSize = 20

  def of(page: Int, size: Int = DefaultSize): PageRequest =
    PageRequest(math.max(page, 1), if (size <= 0) DefaultSize else size)
}
