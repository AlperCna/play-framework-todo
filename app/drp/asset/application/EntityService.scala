package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{Entity, EntityId}
import drp.shared.application.{Page, PageRequest}
import drp.shared.domain.DomainError

/** Application service for entities — validation + duplicate-name guard around the repository. */
trait EntityService {
  def create(name: String, entityType: String): Future[Either[DomainError, Entity]]
  def update(id: EntityId, name: String, entityType: String): Future[Either[DomainError, Entity]]
  def get(id: EntityId): Future[Option[Entity]]
  def list(page: PageRequest): Future[Page[Entity]]
}
