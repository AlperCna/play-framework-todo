package domain.common

/**
 * Tum domain entity'lerinin paylastigi ortak yuz (trait).
 *
 * Bir `case class` olmadigi icin entity'ler bunu sorunsuz mix-in edebilir.
 * Amac: repository ve genel [[domain.common.AuditInfo]] mantiginin (ornegin
 * generic `Table[A]`) entity'leri tek tip gorebilmesi.
 *
 * NOT: Audit'i degistiren davranislar (`markUpdated` vb.) burada DEGIL, her
 * entity'nin kendisinde tanimlanir; cunku bir trait metodu somut entity tipini
 * dondurecek `copy` cagrisini ifade edemez.
 */
trait AuditableEntity {

  /** Kimlik. Yeni (henuz kaydedilmemis) entity'lerde 0'dir; repository atar. */
  def id: Long

  /** Denetim ve soft-delete bilgisi. */
  def audit: AuditInfo

  /** Kisayol: kaydin silinmis sayilip sayilmadigi. */
  def isDeleted: Boolean = audit.isDeleted
}
