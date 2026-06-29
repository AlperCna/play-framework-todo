package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.{AssetGroupRepository, EntityRepository}
import drp.asset.domain.{AssetGroup, AssetGroupId, EntityId}
import drp.shared.application.{Clock, ServiceResult}
import drp.shared.domain.DomainError

@Singleton
class AssetGroupServiceImpl @Inject() (
    entityRepo: EntityRepository,
    groupRepo: AssetGroupRepository,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends AssetGroupService {

  override def get(id: AssetGroupId): Future[Option[AssetGroup]] = groupRepo.get(id)

  override def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]] = groupRepo.listByEntity(entityId)

  override def create(entityId: EntityId, name: String): Future[Either[DomainError, AssetGroup]] =
    (for {
      _     <- ensureEntityExists(entityId)
      _     <- ensureNameFree(entityId, name)
      group <- ServiceResult.fromEither(AssetGroup.create(entityId, name, clock.now()))
      saved <- ServiceResult.fromFuture(groupRepo.add(group))
    } yield saved).value

  override def update(id: AssetGroupId, name: String): Future[Either[DomainError, AssetGroup]] =
    (for {
      existing <- found(id)
      _        <- ensureRenameAllowed(existing, name)
      renamed  <- ServiceResult.fromEither(existing.rename(name, clock.now()))
      saved    <- persist(renamed)
    } yield saved).value

  private def found(id: AssetGroupId): ServiceResult[AssetGroup] =
    ServiceResult.fromOptionF(groupRepo.get(id), DomainError.AssetGroupNotFound(id.value))

  private def persist(g: AssetGroup): ServiceResult[AssetGroup] =
    ServiceResult.fromOptionF(groupRepo.update(g), DomainError.AssetGroupNotFound(g.id.value))

  private def ensureEntityExists(entityId: EntityId): ServiceResult[Unit] =
    ServiceResult(entityRepo.existsById(entityId).map { exists =>
      if (exists) Right(()) else Left(DomainError.EntityNotFound(entityId.value))
    })

  private def ensureNameFree(entityId: EntityId, name: String): ServiceResult[Unit] =
    ServiceResult(groupRepo.existsByEntityAndName(entityId, name.trim).map { exists =>
      if (exists) Left(DomainError.DuplicateAssetGroupName(entityId.value, name.trim)) else Right(())
    })

  private def ensureRenameAllowed(existing: AssetGroup, newName: String): ServiceResult[Unit] =
    if (newName.trim.equalsIgnoreCase(existing.name)) ServiceResult.pure(())
    else ensureNameFree(existing.entityId, newName)
}
