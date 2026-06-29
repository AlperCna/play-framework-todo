package drp.discovery.infrastructure.slick

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase

import drp.discovery.application.DiscoveryStatusFilter
import drp.discovery.application.ports.DiscoveryRepository
import drp.discovery.domain.{CandidateDiscovery, DiscoveryId, DnsStatus, NormalizedValue, SkipReason}
import drp.discovery.infrastructure.tables.CandidateDiscoveriesTable
import drp.shared.application.{Page, PageRequest}
import drp.shared.infrastructure.MonaPgProfile

@Singleton
class SlickDiscoveryRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends DiscoveryRepository
    with CandidateDiscoveriesTable {

  private val dbConfig = dbConfigProvider.get[MonaPgProfile]
  import dbConfig.profile.api._
  private val db = dbConfig.db

  private val insertCols = candidateDiscoveries.map(t => (
    t.entityId, t.assetId, t.value, t.normalizedValue, t.source, t.dnsStatus,
    t.skipReason, t.failedCheckCount, t.httpStatusCode, t.lastCheckedAt, t.nextCheckAt
  ))

  private def toInsertTuple(r: drp.discovery.infrastructure.tables.DiscoveryRow) =
    (r.entityId, r.assetId, r.value, r.normalizedValue, r.source, r.dnsStatus,
     r.skipReason, r.failedCheckCount, r.httpStatusCode, r.lastCheckedAt, r.nextCheckAt)

  override def save(discovery: CandidateDiscovery): Future[CandidateDiscovery] = {
    val insert = (insertCols returning candidateDiscoveries.map(_.id)) += toInsertTuple(toRow(discovery))
    db.run(insert)
      .flatMap(newId => db.run(candidateDiscoveries.filter(_.id === newId).result.head))
      .map(toDomain)
  }

  override def saveAll(discoveries: Seq[CandidateDiscovery]): Future[Seq[CandidateDiscovery]] = {
    if (discoveries.isEmpty) return Future.successful(Seq.empty)
    val tuples = discoveries.map(d => toInsertTuple(toRow(d)))
    db.run((insertCols returning candidateDiscoveries.map(_.id)) ++= tuples).flatMap { ids =>
      db.run(candidateDiscoveries.filter(_.id inSet ids.toSet).sortBy(_.id).result)
    }.map(_.map(toDomain))
  }

  override def get(id: DiscoveryId): Future[Option[CandidateDiscovery]] =
    db.run(candidateDiscoveries.filter(_.id === id.value).result.headOption).map(_.map(toDomain))

  override def findByEntityAndNormalized(
      entityId: Long,
      normalizedValue: NormalizedValue
  ): Future[Option[CandidateDiscovery]] =
    db.run(
      candidateDiscoveries
        .filter(t => t.entityId === entityId && t.normalizedValue === normalizedValue.value)
        .result.headOption
    ).map(_.map(toDomain))

  override def listNormalizedValuesByEntity(entityId: Long): Future[Set[String]] =
    db.run(
      candidateDiscoveries.filter(_.entityId === entityId).map(_.normalizedValue).result
    ).map(_.toSet)

  override def listByEntity(
      entityId: Long,
      statusFilter: Option[DiscoveryStatusFilter],
      page: PageRequest
  ): Future[Page[CandidateDiscovery]] = {
    val baseQuery = candidateDiscoveries.filter(_.entityId === entityId)
    val filtered  = applyStatusFilter(baseQuery, statusFilter)
    val sorted    = filtered.sortBy(_.createdAt.desc)
    for {
      total <- db.run(filtered.length.result)
      rows  <- db.run(sorted.drop(page.offset).take(page.size).result)
    } yield Page(rows.map(toDomain), page.page, page.size, total.toLong)
  }

  private def applyStatusFilter(
      q: Query[DiscoveriesTableDef, drp.discovery.infrastructure.tables.DiscoveryRow, Seq],
      filter: Option[DiscoveryStatusFilter]
  ) = filter match {
    case None => q
    case Some(DiscoveryStatusFilter.PendingValidation) =>
      q.filter(t => t.dnsStatus === DnsStatus.Pending.code && t.skipReason.isEmpty)
    case Some(DiscoveryStatusFilter.Whitelisted) =>
      q.filter(_.skipReason === SkipReason.Whitelisted.code)
    case Some(DiscoveryStatusFilter.InvalidFormat) =>
      q.filter(_.skipReason === SkipReason.InvalidFormat.code)
  }

  private def toRow(d: CandidateDiscovery) = drp.discovery.infrastructure.tables.DiscoveryRow(
    id               = d.id.value,
    entityId         = d.entityId,
    assetId          = d.assetId,
    value            = d.value,
    normalizedValue  = d.normalizedValue.value,
    source           = d.source.code,
    dnsStatus        = d.dnsStatus.code,
    skipReason       = d.skipReason.map(_.code),
    failedCheckCount = d.failedCheckCount,
    httpStatusCode   = d.httpStatusCode,
    lastCheckedAt    = d.lastCheckedAt,
    nextCheckAt      = d.nextCheckAt,
    createdAt        = d.createdAt,
    updatedAt        = d.updatedAt
  )
}
