package todo.shared.application

final case class PageNumber private (value: Int) {
  def offset(size: PageSize): Long = (value - 1).toLong * size.value
}

object PageNumber {
  val First: PageNumber = PageNumber(1)
  def from(n: Int): PageNumber = PageNumber(math.max(1, n))
}

final case class PageSize private (value: Int)

object PageSize {
  val Default: PageSize = PageSize(5)
  val Max: Int = 100
  def from(n: Int): PageSize = PageSize(math.min(math.max(1, n), Max))
}

final case class PageRequest(number: PageNumber, size: PageSize) {
  def offset: Long = number.offset(size)
  def limit: Int = size.value
}

object PageRequest {
  val Default: PageRequest = PageRequest(PageNumber.First, PageSize.Default)

  def from(page: Int, size: Int): PageRequest =
    PageRequest(PageNumber.from(page), PageSize.from(size))
}
