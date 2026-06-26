package drp.analysis.domain

import java.time.Instant

import drp.shared.domain._

final case class ExtractorVersion(value: String) extends AnyVal
object ExtractorVersion {
  def create(value: String): Either[DomainError, ExtractorVersion] =
    CommonValues.nonEmpty("extractorVersion", value).map(ExtractorVersion(_))
}

final case class PageFeatures(
    id: PageFeatureId,
    crawlResultId: CrawlResultId,
    title: Option[String],
    hasForm: Boolean,
    hasPasswordInput: Boolean,
    brandNameFound: Boolean,
    domSummary: Metadata,
    extractorVersion: ExtractorVersion,
    createdAt: Instant
)
