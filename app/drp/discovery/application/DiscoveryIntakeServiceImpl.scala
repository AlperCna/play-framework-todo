package drp.discovery.application

import java.time.Instant

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.AssetReadPort
import drp.asset.domain.EntityId
import drp.discovery.application.ports.{DiscoveryRepository, PermutationProvider}
import drp.discovery.domain.{CandidateDiscovery, DiscoveryId, DiscoverySource, SkipReason}
import drp.shared.application.{Page, PageRequest, ServiceResult}
import drp.shared.domain.DomainError

@Singleton
class DiscoveryIntakeServiceImpl @Inject() (
    assetReadPort: AssetReadPort,
    repo: DiscoveryRepository,
    permutationProvider: PermutationProvider
)(implicit ec: ExecutionContext)
    extends DiscoveryIntakeService {

  override def submitManual(
      entityId: Long,
      assetId: Option[Long],
      rawValue: String
  ): ServiceResult[CandidateDiscovery] = {
    if (rawValue.trim.isEmpty)
      return ServiceResult.fromEither(Left(DomainError.EmptyDiscoveryValue))

    for {
      entityWithAssets <- ServiceResult.fromOptionF(
        assetReadPort.resolveEntityWithAssets(EntityId(entityId)),
        DomainError.EntityNotFound(entityId)
      )
      _ <- ServiceResult.fromEither(assetId match {
        case None => Right(())
        case Some(aid) =>
          if (entityWithAssets.assets.exists(_.id == aid)) Right(())
          else Left(DomainError.AssetEntityMismatch(aid, entityId))
      })
      exclusions <- ServiceResult.fromFuture(assetReadPort.activeExclusions(EntityId(entityId)))
      result <- {
        val discovery = CandidateDiscovery.intake(entityId, assetId, rawValue, DiscoverySource.Manual)
        val withSkip = if (discovery.normalizedValue.value.startsWith("invalid:"))
          discovery.copy(skipReason = Some(SkipReason.InvalidFormat))
        else
          ExclusionMatcher.matches(discovery.normalizedValue, exclusions)
            .map(r => discovery.copy(skipReason = Some(r)))
            .getOrElse(discovery)
        ServiceResult.fromFuture(
          repo.findByEntityAndNormalized(entityId, withSkip.normalizedValue).flatMap {
            case Some(existing) => Future.successful(existing)
            case None           => repo.save(withSkip)
          }
        )
      }
    } yield result
  }

  override def requestPermutation(entityId: Long, assetId: Long): ServiceResult[Int] =
    for {
      entityWithAssets <- ServiceResult.fromOptionF(
        assetReadPort.resolveEntityWithAssets(EntityId(entityId)),
        DomainError.EntityNotFound(entityId)
      )
      asset <- ServiceResult.fromEither(
        entityWithAssets.assets.find(_.id == assetId)
          .toRight(DomainError.AssetNotFound(assetId))
      )
      _ <- ServiceResult.fromEither(
        if (asset.assetType == "domain") Right(())
        else Left(DomainError.AssetNotDomainType(assetId))
      )
      _ <- ServiceResult.fromEither(
        if (asset.isActive) Right(())
        else Left(DomainError.AssetNotActive(assetId))
      )
      rawResults <- ServiceResult(
        permutationProvider.generateLookAlikes(asset.value)
          .map(Right(_))
          .recover { case ex => Left(DomainError.PermutationProviderFailure(ex.getMessage)) }
      )
      existingNorms <- ServiceResult.fromFuture(repo.listNormalizedValuesByEntity(entityId))
      exclusions    <- ServiceResult.fromFuture(assetReadPort.activeExclusions(EntityId(entityId)))
      count <- {
        val now = Instant.now()
        val toSave = rawResults
          .filter(_.trim.nonEmpty)
          .map(raw => CandidateDiscovery.intake(entityId, Some(assetId), raw, DiscoverySource.Permutation, now))
          .filter(d => !existingNorms.contains(d.normalizedValue.value))
          .distinctBy(_.normalizedValue.value)
          .map { d =>
            if (d.normalizedValue.value.startsWith("invalid:"))
              d.copy(skipReason = Some(SkipReason.InvalidFormat))
            else
              ExclusionMatcher.matches(d.normalizedValue, exclusions)
                .map(r => d.copy(skipReason = Some(r)))
                .getOrElse(d)
          }
        ServiceResult.fromFuture(
          repo.saveAll(toSave).map(_.size)
        )
      }
    } yield count

  override def listDiscoveries(
      entityId: Long,
      statusFilter: Option[DiscoveryStatusFilter],
      page: PageRequest
  ): Future[Page[CandidateDiscovery]] =
    repo.listByEntity(entityId, statusFilter, page)

  override def getDiscovery(id: DiscoveryId): ServiceResult[CandidateDiscovery] =
    ServiceResult.fromOptionF(repo.get(id), DomainError.DiscoveryNotFound(id.value))
}
