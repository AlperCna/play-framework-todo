package drp.asset.application.ports

import scala.concurrent.Future

import drp.asset.domain.Entity

/**
 * Port for persisting and reading protected entities. The asset module is the single writer
 * of the `entities` table; the asset service uses `existsById` to validate an owning entity.
 */
trait EntityRepository {

  /** Persist a new entity; returns it with the database-assigned id. */
  def create(entity: Entity): Future[Entity]

  /** All entities, ordered by id (bulk read — no per-entity query). */
  def listAll(): Future[Seq[Entity]]

  /** Whether an entity with this id exists. */
  def existsById(id: Long): Future[Boolean]
}
