package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.{Entity, EntityId}
import drp.shared.application.{Page, PageRequest}

/** Persistence port for entities (the `asset` module is the single writer of `entities`). */
trait EntityRepository {
  def add(e: Entity): Future[Entity]
  def get(id: EntityId): Future[Option[Entity]]
  def existsById(id: EntityId): Future[Boolean]
  def existsByName(name: String): Future[Boolean]
  def update(e: Entity): Future[Option[Entity]]
  def list(page: PageRequest): Future[Page[Entity]]
}
