package persistence.db

import java.sql.Timestamp
import java.time.Instant

import domain.common.AuditInfo

/**
 * Cross-cutting: TUM entity'lerde ORTAK olan audit + soft-delete kolonlarinin
 * Row <-> domain cevrimi. Eskiden tek `Mappers` object'inin private helper'lariydi;
 * artik her entity mapper'i bunu paylasir, boylece audit semantigi tek yerden evrilir.
 *
 * Ayrica TIP cevrimi: domain `Instant` <-> JDBC-yerel `java.sql.Timestamp`
 * (Slick'in string-parse etmeyen yerlesik destegi icin; bkz. PERSISTENCE.md).
 */
object AuditMapper {

  /** domain `Instant` -> JDBC `Timestamp`. */
  def ts(i: Instant): Timestamp = Timestamp.from(i)

  /** Opsiyonel `Instant` -> opsiyonel `Timestamp`. */
  def tsOpt(i: Option[Instant]): Option[Timestamp] = i.map(Timestamp.from)

  /** Row'daki 7 audit alanindan domain `AuditInfo`'yu kurar. */
  def toAudit(
      createdAt: Timestamp,
      createdBy: String,
      updatedAt: Option[Timestamp],
      updatedBy: String,
      isDeleted: Boolean,
      deletedAt: Option[Timestamp],
      deletedBy: String
  ): AuditInfo =
    AuditInfo(
      createdAt.toInstant, createdBy, updatedAt.map(_.toInstant), updatedBy,
      isDeleted, deletedAt.map(_.toInstant), deletedBy
    )
}
