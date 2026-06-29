package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.{AssetGroupRepository, AssetRepository, EntityRepository}
import drp.asset.domain.{Asset, AssetGroupId, AssetId, AssetMetadata, EntityId}
import drp.shared.application.{Clock, ServiceResult}
import drp.shared.domain.DomainError

@Singleton
class AssetServiceImpl @Inject() (
    entityRepo: EntityRepository,
    assetRepo: AssetRepository,
    groupRepo: AssetGroupRepository,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends AssetService {

  override def get(id: AssetId): Future[Option[Asset]] = assetRepo.get(id)

  override def listByEntity(entityId: EntityId): Future[Seq[Asset]] = assetRepo.listByEntity(entityId)

  override def create(
      entityId: EntityId,
      assetType: String,
      value: String,
      metadata: AssetMetadata,
      assetGroupId: Option[AssetGroupId] = None
  ): Future[Either[DomainError, Asset]] =
    (for {
      _     <- ensureEntityExists(entityId)
      _     <- ensureGroupValid(entityId, assetGroupId)
      asset <- ServiceResult.fromEither(Asset.create(entityId, assetType, value, metadata, clock.now(), assetGroupId))
      _     <- ensureNotDuplicate(asset)
      saved <- ServiceResult.fromFuture(assetRepo.add(asset))
    } yield saved).value

  override def update(
      id: AssetId,
      assetType: String,
      value: String,
      metadata: AssetMetadata,
      assetGroupId: Option[AssetGroupId] = None
  ): Future[Either[DomainError, Asset]] =
    (for {
      existing <- found(id)
      _        <- ensureGroupValid(existing.entityId, assetGroupId)
      edited   <- ServiceResult.fromEither(existing.edit(assetType, value, metadata, clock.now(), assetGroupId))
      _        <- ensureNotDuplicateOnEdit(existing, edited)
      saved    <- persist(edited)
    } yield saved).value

  private def found(id: AssetId): ServiceResult[Asset] =
    ServiceResult.fromOptionF(assetRepo.get(id), DomainError.AssetNotFound(id.value))

  private def persist(a: Asset): ServiceResult[Asset] =
    ServiceResult.fromOptionF(assetRepo.update(a), DomainError.AssetNotFound(a.id.value))

  private def ensureEntityExists(entityId: EntityId): ServiceResult[Unit] =
    ServiceResult(entityRepo.existsById(entityId).map { exists =>
      if (exists) Right(()) else Left(DomainError.EntityNotFound(entityId.value))
    })

  // FR-005: a group (if any) must exist AND belong to the same entity as the asset.
  private def ensureGroupValid(entityId: EntityId, groupId: Option[AssetGroupId]): ServiceResult[Unit] =
    groupId match {
      case None => ServiceResult.pure(())
      case Some(gid) =>
        ServiceResult(groupRepo.get(gid).map {
          case None                                  => Left(DomainError.AssetGroupNotFound(gid.value))
          case Some(g) if g.entityId != entityId     => Left(DomainError.AssetGroupEntityMismatch(g.entityId.value, entityId.value))
          case Some(_)                               => Right(())
        })
    }

  private def ensureNotDuplicate(a: Asset): ServiceResult[Unit] =
    ServiceResult(assetRepo.existsActive(a.entityId, a.assetType, a.value).map { dup =>
      if (dup) Left(DomainError.DuplicateAsset(a.entityId.value, a.assetType.code, a.value)) else Right(())
    })

  private def ensureNotDuplicateOnEdit(existing: Asset, edited: Asset): ServiceResult[Unit] =
    if (existing.assetType == edited.assetType && existing.value == edited.value) ServiceResult.pure(())
    else ensureNotDuplicate(edited)
}
