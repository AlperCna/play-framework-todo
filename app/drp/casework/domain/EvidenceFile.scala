package drp.casework.domain

import java.time.Instant

import drp.shared.domain._

sealed trait EvidenceFileType { def value: String }
object EvidenceFileType {
  case object Screenshot  extends EvidenceFileType { val value = "screenshot" }
  case object HtmlArchive extends EvidenceFileType { val value = "html_archive" }
  case object DomSnapshot extends EvidenceFileType { val value = "dom_snapshot" }
  case object OcrOutput   extends EvidenceFileType { val value = "ocr_output" }
  case object Favicon     extends EvidenceFileType { val value = "favicon" }
  case object Logo        extends EvidenceFileType { val value = "logo" }
}

final case class EvidenceFile(
    id: EvidenceFileId,
    candidateId: CandidateId,
    crawlResultId: Option[CrawlResultId],
    fileType: EvidenceFileType,
    storageRef: StorageRef,
    contentHash: Option[ContentHash],
    capturedAt: Instant,
    createdAt: Instant
)
