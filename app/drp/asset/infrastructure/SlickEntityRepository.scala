package drp.asset.infrastructure

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase

import drp.asset.application.ports.EntityRepository
import drp.asset.domain.Entity
import drp.shared.infrastructure.MonaPgProfile

/** PostgreSQL adapter for `EntityRepository`, bound to the dedicated `drp` Slick database. */
@Singleton
class SlickEntityRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends EntityRepository
    with HasDatabaseConfigProvider[MonaPgProfile]
    with EntitiesTable {

  import profile.api._

  override def create(entity: Entity): Future[Entity] = {
    // Insert only name + type; id/created_at/updated_at are DB-assigned.
    val insert =
      (entities.map(e => (e.name, e.entityType)) returning entities.map(_.id)) += ((entity.name, entity.entityType))
    db.run(insert).map(newId => entity.copy(id = newId))
  }

  override def listAll(): Future[Seq[Entity]] =
    db.run(entities.sortBy(_.id).result).map(_.map(toDomain))

  override def existsById(id: Long): Future[Boolean] =
    db.run(entities.filter(_.id === id).exists.result)

  private def toDomain(r: EntityRow): Entity =
    Entity(r.id, r.name, r.entityType, r.createdAt, r.updatedAt)
}
