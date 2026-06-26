package drp.platform.storage.domain

import java.time.Instant

import drp.shared.domain._

sealed trait BlobFileType { def value: String }
object BlobFileType {
  case object HtmlArchive  extends BlobFileType { val value = "html_archive" }
  case object Screenshot   extends BlobFileType { val value = "screenshot" }
  case object DomSnapshot  extends BlobFileType { val value = "dom_snapshot" }
  case object OcrOutput    extends BlobFileType { val value = "ocr_output" }
  case object Favicon      extends BlobFileType { val value = "favicon" }
  case object Logo         extends BlobFileType { val value = "logo" }
  case object CrawlBundle  extends BlobFileType { val value = "crawl_bundle" }
}

sealed trait CompressionType { def value: String }
object CompressionType {
  case object NoCompression extends CompressionType { val value = "none" }
  case object Gzip          extends CompressionType { val value = "gzip" }
}

final case class ContentType(value: String) extends AnyVal
object ContentType {
  def create(value: String): Either[DomainError, ContentType] =
    CommonValues.nonEmpty("contentType", value).map(ContentType(_))
}

final case class BlobObject(
    id: BlobStorageId,
    storageRef: StorageRef,
    fileType: BlobFileType,
    contentType: ContentType,
    sizeBytes: Long,
    contentHash: Option[ContentHash],
    compression: CompressionType,
    createdAt: Instant
)

object BlobObject {
  def create(
      id: BlobStorageId,
      storageRef: StorageRef,
      fileType: BlobFileType,
      contentType: ContentType,
      sizeBytes: Long,
      contentHash: Option[ContentHash],
      compression: CompressionType,
      createdAt: Instant
  ): Either[DomainError, BlobObject] =
    if (sizeBytes >= 0) Right(BlobObject(id, storageRef, fileType, contentType, sizeBytes, contentHash, compression, createdAt))
    else Left(DomainError.NegativeValue("sizeBytes", sizeBytes))
}
