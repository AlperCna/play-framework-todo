package pagination

/**
 * Sayfalama UI'inda gosterilecek sayfa-numarasi PENCERESI.
 *
 * Cok sayfali listelerde 1..N'in tamamini basmak yerine, mevcut sayfanin
 * etrafinda dar bir aralik gosteririz (orn. current +/- radius). Boylece 10.000
 * sayfali bir tabloda bile kontrol kompakt kalir (buyuk tabloya olceklenir).
 */
object PageWindow {

  /** Mevcut sayfanin etrafindaki gorunur sayfa numaralari (sinirlara kirpilmis). */
  def around(page: Page[_], radius: Int = 2): Seq[Int] = {
    val lo = math.max(1, page.pageNumber - radius)
    val hi = math.min(page.totalPages, page.pageNumber + radius)
    lo to hi
  }
}
