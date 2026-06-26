package todo.shared.application

object PageWindow {

  def around(page: Page[_], radius: Int = 2): Seq[Int] = {
    val lo = math.max(1, page.pageNumber - radius)
    val hi = math.min(page.totalPages, page.pageNumber + radius)
    lo to hi
  }
}
