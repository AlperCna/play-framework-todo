package drp.shared.domain

import java.time.Instant

final case class AuditInfo(createdAt: Instant, updatedAt: Instant)

final case class StorageRef private (value: String) extends AnyVal
object StorageRef {
  def create(value: String): Either[DomainError, StorageRef] =
    CommonValues.nonEmpty("storageRef", value).map(StorageRef(_))

  def pgBlob(candidateId: CandidateId, crawlRunId: CrawlResultId, fileType: String, ext: String): Either[DomainError, StorageRef] =
    for {
      file <- CommonValues.nonEmpty("fileType", fileType)
      extension <- CommonValues.nonEmpty("ext", ext)
      ref <- create(s"pg://evidence/${candidateId.value}/${crawlRunId.value}/$file.$extension")
    } yield ref
}

final case class ContentHash private (value: String) extends AnyVal
object ContentHash {
  def create(value: String): Either[DomainError, ContentHash] =
    CommonValues.nonEmpty("contentHash", value).map(ContentHash(_))
}

final case class Score private (value: BigDecimal) extends AnyVal
object Score {
  def create(field: String, value: BigDecimal): Either[DomainError, Score] =
    if (value >= 0 && value <= 1) Right(Score(value))
    else Left(DomainError.InvalidRange(field, 0, 1))
}

sealed trait MetadataValue
object MetadataValue {
  final case class Text(value: String) extends MetadataValue
  final case class Number(value: BigDecimal) extends MetadataValue
  final case class Bool(value: Boolean) extends MetadataValue
  final case class Obj(value: Map[String, MetadataValue]) extends MetadataValue
  final case class Arr(value: Vector[MetadataValue]) extends MetadataValue
}

final case class Metadata(values: Map[String, MetadataValue]) extends AnyVal
object Metadata {
  val empty: Metadata = Metadata(Map.empty)
}

object CommonValues {
  def nonEmpty(field: String, value: String): Either[DomainError, String] =
    if (value.trim.nonEmpty) Right(value.trim) else Left(DomainError.EmptyField(field))
}
