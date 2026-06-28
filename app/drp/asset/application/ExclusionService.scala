package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{AssetDomainError, Exclusion}

/** Use cases for exclusions: register a new exclusion under an entity and list an entity's exclusions. */
trait ExclusionService {

  def register(
      entityId: Long,
      value: String,
      matchType: String,
      reason: String
  ): Future[Either[AssetDomainError, Exclusion]]

  def listByEntity(entityId: Long): Future[Seq[Exclusion]]
}
