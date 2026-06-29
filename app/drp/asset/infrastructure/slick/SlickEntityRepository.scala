package drp.asset.infrastructure.slick

import java.time.Instant

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase

import drp.asset.application.ports.EntityRepository
import drp.asset.domain.{Entity, EntityId, EntityType}
import drp.shared.application.{Page, PageRequest}
import drp.shared.infrastructure.MonaPgProfile

/** Persisted row shape for `entities` (infrastructure-only; never crosses the module boundary). */
private[slick] final case class EntityRow(
    id: Long,
    name: String,
    entityType: String,
    createdAt: Instant,
    updatedAt: Instant
)

/**
 * Slick adapter for `EntityRepository` on the named `drp` PostgreSQL datasource (MonaPgProfile).
 * Inserts/updates only the analyst-set columns; `id`, `created_at`, `updated_at` are DB-managed
 * (BIGSERIAL + DEFAULT now() + `set_updated_at` trigger).
 */
@Singleton
class SlickEntityRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends EntityRepository {

  private val dbConfig = dbConfigProvider.get[MonaPgProfile]
  import dbConfig.profile.api._
  private val db = dbConfig.db

  private class EntitiesTable(tag: Tag) extends Table[EntityRow](tag, "entities") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def entityType = column[String]("type")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    def * = (id, name, entityType, createdAt, updatedAt).mapTo[EntityRow]
  }

  private val entities = TableQuery[EntitiesTable]

  private def toDomain(r: EntityRow): Entity =
    Entity(EntityId(r.id), r.name, EntityType.fromCode(r.entityType), r.createdAt, r.updatedAt)

  override def add(e: Entity): Future[Entity] = {
    val insert = (entities.map(t => (t.name, t.entityType)) returning entities.map(_.id)) +=
      ((e.name, EntityType.toCode(e.entityType)))
    db.run(insert)
      .flatMap(newId => db.run(entities.filter(_.id === newId).result.head))
      .map(toDomain)
  }

  override def get(id: EntityId): Future[Option[Entity]] =
    db.run(entities.filter(_.id === id.value).result.headOption).map(_.map(toDomain))

  override def existsById(id: EntityId): Future[Boolean] =
    db.run(entities.filter(_.id === id.value).exists.result)

  override def existsByName(name: String): Future[Boolean] =
    db.run(entities.filter(_.name === name.trim).exists.result)

  override def update(e: Entity): Future[Option[Entity]] = {
    val q = entities.filter(_.id === e.id.value).map(t => (t.name, t.entityType))
      .update((e.name, EntityType.toCode(e.entityType)))
    db.run(q).flatMap {
      case 0 => Future.successful(None)
      case _ => db.run(entities.filter(_.id === e.id.value).result.headOption).map(_.map(toDomain))
    }
  }

  override def list(page: PageRequest): Future[Page[Entity]] =
    for {
      total <- db.run(entities.length.result)
      rows  <- db.run(entities.sortBy(_.id).drop(page.offset).take(page.size).result)
    } yield Page(rows.map(toDomain), page.page, page.size, total.toLong)
}
