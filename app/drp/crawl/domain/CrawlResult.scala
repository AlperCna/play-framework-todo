package drp.crawl.domain

import java.time.Instant

import drp.shared.domain._

final case class HttpStatus private (value: Int) extends AnyVal
object HttpStatus {
  def create(value: Int): Either[DomainError, HttpStatus] =
    if (value >= 100 && value <= 599) Right(HttpStatus(value))
    else Left(DomainError.InvalidHttpStatus(value))
}

final case class RedirectHop(from: String, to: String, statusCode: Option[HttpStatus])
object RedirectHop {
  def create(from: String, to: String, statusCode: Option[HttpStatus]): Either[DomainError, RedirectHop] =
    for {
      validFrom <- CommonValues.nonEmpty("redirect.from", from)
      validTo   <- CommonValues.nonEmpty("redirect.to", to)
    } yield RedirectHop(validFrom, validTo, statusCode)
}

final case class RedirectChain(hops: Vector[RedirectHop]) extends AnyVal
object RedirectChain {
  val empty: RedirectChain = RedirectChain(Vector.empty)
}

final case class NetworkObservation(
    resolvedIp: Option[String],
    asn: Option[String],
    asnOrg: Option[String],
    hostingProvider: Option[String],
    ipCountry: Option[String]
)

final case class CrawlResult(
    id: CrawlResultId,
    candidateId: CandidateId,
    httpStatus: HttpStatus,
    redirectChain: RedirectChain,
    finalUrl: String,
    network: NetworkObservation,
    storageRef: StorageRef,
    metadata: Metadata,
    crawledAt: Instant,
    createdAt: Instant
)

object CrawlResult {
  def create(
      id: CrawlResultId,
      candidateId: CandidateId,
      httpStatus: HttpStatus,
      redirectChain: RedirectChain,
      finalUrl: String,
      network: NetworkObservation,
      storageRef: StorageRef,
      metadata: Metadata,
      crawledAt: Instant,
      createdAt: Instant
  ): Either[DomainError, CrawlResult] =
    CommonValues.nonEmpty("finalUrl", finalUrl).map { validFinalUrl =>
      CrawlResult(id, candidateId, httpStatus, redirectChain, validFinalUrl, network, storageRef, metadata, crawledAt, createdAt)
    }
}
