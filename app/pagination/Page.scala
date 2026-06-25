package pagination

/**
 * Sayfalama SONUCU (result-side) — domain-agnostik core.
 *
 * Bir sayfalik veri (`items`) + navigasyon metadatasi. Hicbir domain tipini
 * import etmez; TUM domain'ler ayni soyutlamayi paylasir (her domain'e
 * uyarlanabilirligin merkezi). `+A` (kovaryant) oldugundan `Page[TaskItem]`,
 * `Page[_]` bekleyen ortak Twirl partial'ina sorunsuz gecer.
 */
final case class Page[+A](
    items: Seq[A],
    pageNumber: Int, // 1-tabanli (current)
    pageSize: Int,
    totalCount: Long
) {

  /** Toplam sayfa sayisi (bos sonucta 0). */
  val totalPages: Int =
    if (totalCount == 0) 0 else math.ceil(totalCount.toDouble / pageSize).toInt

  def hasPrev: Boolean = pageNumber > 1
  def hasNext: Boolean = pageNumber < totalPages
  def isEmpty: Boolean = items.isEmpty
  def nonEmpty: Boolean = items.nonEmpty

  /**
   * Sayfa metadatasini KORUYARAK icerigi donustur (functor). Domain entity'sini
   * view-model'e cevirirken sayfalama bilgisi kaybolmaz — katmanlar arasi composition.
   */
  def map[B](f: A => B): Page[B] = copy(items = items.map(f))
}

object Page {

  /** Pencere (items) + istek + toplam adet'ten bir sayfa kurar (repository kullanimi). */
  def from[A](items: Seq[A], request: PageRequest, totalCount: Long): Page[A] =
    Page(items, request.number.value, request.size.value, totalCount)

  /** Sonuc bos oldugunda (totalCount 0) kullanilabilen bos sayfa. */
  def empty[A](request: PageRequest): Page[A] =
    Page(Nil, request.number.value, request.size.value, 0L)
}
