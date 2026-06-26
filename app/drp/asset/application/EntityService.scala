package drp.asset.application

import scala.concurrent.Future

import drp.asset.domain.{AssetDomainError, Entity}

/** Use cases for protected entities: register a new entity and list all registered entities. */
trait EntityService {

  def register(name: String, entityType: String): Future[Either[AssetDomainError, Entity]]

  def list(): Future[Seq[Entity]]
}
