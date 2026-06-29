package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.EntityRepository
import drp.asset.domain.{Entity, EntityId}
import drp.shared.application.{Clock, Page, PageRequest, ServiceResult}
import drp.shared.domain.DomainError

@Singleton
class EntityServiceImpl @Inject() (repo: EntityRepository, clock: Clock)(implicit ec: ExecutionContext)
    extends EntityService {

  override def get(id: EntityId): Future[Option[Entity]] = repo.get(id)

  override def list(page: PageRequest): Future[Page[Entity]] = repo.list(page)

  override def create(name: String, entityType: String): Future[Either[DomainError, Entity]] =
    (for {
      _      <- ensureNameFree(name)
      entity <- ServiceResult.fromEither(Entity.create(name, entityType, clock.now()))
      saved  <- ServiceResult.fromFuture(repo.add(entity))
    } yield saved).value

  override def update(id: EntityId, name: String, entityType: String): Future[Either[DomainError, Entity]] =
    (for {
      existing <- found(id)
      _        <- ensureRenameAllowed(existing, name)
      edited   <- ServiceResult.fromEither(existing.edit(name, entityType, clock.now()))
      saved    <- persist(edited)
    } yield saved).value

  private def found(id: EntityId): ServiceResult[Entity] =
    ServiceResult.fromOptionF(repo.get(id), DomainError.EntityNotFound(id.value))

  private def persist(e: Entity): ServiceResult[Entity] =
    ServiceResult.fromOptionF(repo.update(e), DomainError.EntityNotFound(e.id.value))

  private def ensureNameFree(name: String): ServiceResult[Unit] =
    ServiceResult(repo.existsByName(name.trim).map { exists =>
      if (exists) Left(DomainError.DuplicateEntityName(name.trim)) else Right(())
    })

  // Renaming to the same name is a no-op; only a *changed* name needs the duplicate check.
  private def ensureRenameAllowed(existing: Entity, newName: String): ServiceResult[Unit] =
    if (newName.trim.equalsIgnoreCase(existing.name)) ServiceResult.pure(())
    else ensureNameFree(newName)
}
