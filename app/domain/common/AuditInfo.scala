package domain.common

import java.time.Instant

/**
 * Denetim (audit) ve soft-delete bilgisini tasiyan deger nesnesi (value object).
 *
 * NEDEN AYRI BIR NESNE? Scala'da bir `case class` baska bir `case class`'tan
 * turetilemez; ayrica `copy` davranisini bozar. Bu yuzden DOMAIN-SPEC'teki
 * `AuditableEntity` taban sinifini miras yerine KOMPOZISYON ile kuruyoruz:
 * her entity icinde `audit: AuditInfo` alani olarak gomulur.
 *
 * SAFLIK: Bu nesnenin hicbir metodu `Instant.now()` cagirmaz. Zaman disaridan
 * (servis katmanindan) parametre olarak gelir; boylece domain deterministik
 * ve test edilebilir kalir.
 *
 * @param createdAt Olusturulma zamani.
 * @param createdBy Olusturan kullanici.
 * @param updatedAt Son guncelleme zamani (henuz guncellenmediyse None).
 * @param updatedBy Son guncelleyen.
 * @param isDeleted Soft-delete bayragi.
 * @param deletedAt Silinme zamani (silinmediyse None).
 * @param deletedBy Silen kullanici.
 */
case class AuditInfo(
    createdAt: Instant,
    createdBy: String,
    updatedAt: Option[Instant] = None,
    updatedBy: String = "",
    isDeleted: Boolean = false,
    deletedAt: Option[Instant] = None,
    deletedBy: String = ""
) {

  /** `Updated(by)`: son guncelleme bilgisini yazar. */
  def updated(now: Instant, by: String): AuditInfo =
    copy(updatedAt = Some(now), updatedBy = by)

  /** `Deleted(by)`: tam soft-delete (audit bilgisiyle birlikte). */
  def deleted(now: Instant, by: String): AuditInfo =
    copy(isDeleted = true, deletedAt = Some(now), deletedBy = by)

  /** `SoftDelete()`: sadece bayragi kaldirir (audit alanlarina dokunmaz). */
  def softDelete(): AuditInfo =
    copy(isDeleted = true)

  /** `Restore()`: silmeyi geri alir. */
  def restore(): AuditInfo =
    copy(isDeleted = false, deletedAt = None, deletedBy = "")
}

object AuditInfo {

  /** `Created(by)`: bir entity ilk yaratildiginda ilk audit bilgisini kurar. */
  def create(now: Instant, by: String): AuditInfo =
    AuditInfo(createdAt = now, createdBy = by)
}
