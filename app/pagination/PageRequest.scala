package pagination

/**
 * Sayfalama ISTEGI (request-side) — domain-agnostik core.
 *
 * Primitive obsession'dan kacinmak icin sayfa numarasi ve boyutu ayri
 * value-object'lerde toplanir; HER IKISI DE kendini dogrular (private ctor +
 * clamp eden smart constructor). Boylece servis/repository katmani DAIMA gecerli
 * bir istek gorur — gecersiz deger uretmek tip seviyesinde imkansiz.
 *
 * Pagination'da "reddetmek" yerine "clamp etmek" tercih edilir (out-of-range page
 * ya da devasa size kullaniciyi hataya dusurmek yerine sinira cekilir).
 */
final case class PageNumber private (value: Int) {

  /** Bu sayfanin (verilen boyutla) atlanacak kayit sayisi: (n - 1) * size. */
  def offset(size: PageSize): Long = (value - 1).toLong * size.value
}

object PageNumber {
  val First: PageNumber = PageNumber(1)

  /** < 1 ise 1'e clamp. */
  def from(n: Int): PageNumber = PageNumber(math.max(1, n))
}

final case class PageSize private (value: Int)

object PageSize {
  // Sayfa basina varsayilan kayit. `conf/routes`'taki `GET /tasks ... size ?= 5`
  // ile AYNI tutulmali (HTTP kenarindaki varsayilan oradan gelir).
  val Default: PageSize = PageSize(5)

  /** Ust sinir: kotuye kullanimi (tek istekte devasa sayfa) onler. */
  val Max: Int = 100

  /** [1, Max] araligina clamp. */
  def from(n: Int): PageSize = PageSize(math.min(math.max(1, n), Max))
}

/** Bir sayfalik veriyi getirmek icin gereken (dogrulanmis) istek. */
final case class PageRequest(number: PageNumber, size: PageSize) {

  /** Repository penceresi icin: kac kayit atlanacak. */
  def offset: Long = number.offset(size)

  /** Repository penceresi icin: kac kayit alinacak. */
  def limit: Int = size.value
}

object PageRequest {
  val Default: PageRequest = PageRequest(PageNumber.First, PageSize.Default)

  /** HTTP kenarindan gelen ham `?page=&size=` Int'lerini tek noktada dogrula + clamp et. */
  def from(page: Int, size: Int): PageRequest =
    PageRequest(PageNumber.from(page), PageSize.from(size))
}
