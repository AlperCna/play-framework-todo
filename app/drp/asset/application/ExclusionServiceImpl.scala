package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.{EntityRepository, ExclusionRepository}
import drp.asset.domain.{EntityId, Exclusion, ExclusionId}
import drp.shared.application.{Clock, ServiceResult}
import drp.shared.domain.DomainError

@Singleton
class ExclusionServiceImpl @Inject() (
    entityRepo: EntityRepository,
    exclusionRepo: ExclusionRepository,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends ExclusionService {

  override def get(id: ExclusionId): Future[Option[Exclusion]] = exclusionRepo.get(id)

  override def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]] =
    exclusionRepo.listActiveByEntity(entityId)

  override def create(
      entityId: EntityId,
      value: String,
      matchType: String,
      reason: String
  ): Future[Either[DomainError, Exclusion]] =
    (for {
      _     <- ensureEntityExists(entityId)
      x     <- ServiceResult.fromEither(Exclusion.create(entityId, value, matchType, reason, clock.now()))
      _     <- ensureNotDuplicate(x)
      saved <- ServiceResult.fromFuture(exclusionRepo.add(x))
    } yield saved).value

  override def update(
      id: ExclusionId,
      value: String,
      matchType: String,
      reason: String
  ): Future[Either[DomainError, Exclusion]] =
    (for {
      existing <- found(id)
      edited   <- ServiceResult.fromEither(existing.edit(value, matchType, reason, clock.now()))
      _        <- ensureNotDuplicateOnEdit(existing, edited)
      saved    <- persist(edited)
    } yield saved).value

  private def found(id: ExclusionId): ServiceResult[Exclusion] =
    ServiceResult.fromOptionF(exclusionRepo.get(id), DomainError.ExclusionNotFound(id.value))

  private def persist(x: Exclusion): ServiceResult[Exclusion] =
    ServiceResult.fromOptionF(exclusionRepo.update(x), DomainError.ExclusionNotFound(x.id.value))

  private def ensureEntityExists(entityId: EntityId): ServiceResult[Unit] =
    ServiceResult(entityRepo.existsById(entityId).map { exists =>
      if (exists) Right(()) else Left(DomainError.EntityNotFound(entityId.value))
    })

  private def ensureNotDuplicate(x: Exclusion): ServiceResult[Unit] =
    ServiceResult(exclusionRepo.existsActive(x.entityId, x.value, x.matchType).map { dup =>
      if (dup) Left(DomainError.DuplicateExclusion(x.entityId.value, x.value, x.matchType.code)) else Right(())
    })

  private def ensureNotDuplicateOnEdit(existing: Exclusion, edited: Exclusion): ServiceResult[Unit] =
    if (existing.value == edited.value && existing.matchType == edited.matchType) ServiceResult.pure(())
    else ensureNotDuplicate(edited)
}
