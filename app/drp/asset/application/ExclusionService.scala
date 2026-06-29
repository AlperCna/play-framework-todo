package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{EntityId, Exclusion, ExclusionId}
import drp.shared.domain.DomainError

/** Application service for exclusions — entity-scoped; stores verbatim; never evaluates match semantics. */
trait ExclusionService {
  def create(entityId: EntityId, value: String, matchType: String, reason: String): Future[Either[DomainError, Exclusion]]
  def update(id: ExclusionId, value: String, matchType: String, reason: String): Future[Either[DomainError, Exclusion]]
  def get(id: ExclusionId): Future[Option[Exclusion]]
  def listActiveByEntity(entityId: EntityId): Future[Seq[Exclusion]]
}
