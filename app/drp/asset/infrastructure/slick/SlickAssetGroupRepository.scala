package drp.asset.infrastructure.slick

import java.time.Instant

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase

import drp.asset.application.ports.AssetGroupRepository
import drp.asset.domain.{AssetGroup, AssetGroupId, EntityId}
import drp.shared.infrastructure.MonaPgProfile

private[slick] final case class AssetGroupRow(
    id: Long,
    entityId: Long,
    name: String,
    createdAt: Instant,
    updatedAt: Instant
)

/** Slick adapter for `AssetGroupRepository` on the `drp` datasource. */
@Singleton
class SlickAssetGroupRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends AssetGroupRepository {

  private val dbConfig = dbConfigProvider.get[MonaPgProfile]
  import dbConfig.profile.api._
  private val db = dbConfig.db

  private class AssetGroupsTable(tag: Tag) extends Table[AssetGroupRow](tag, "asset_groups") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def entityId = column[Long]("entity_id")
    def name = column[String]("name")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    def * = (id, entityId, name, createdAt, updatedAt).mapTo[AssetGroupRow]
  }

  private val groups = TableQuery[AssetGroupsTable]

  private def toDomain(r: AssetGroupRow): AssetGroup =
    AssetGroup(AssetGroupId(r.id), EntityId(r.entityId), r.name, r.createdAt, r.updatedAt)

  override def add(g: AssetGroup): Future[AssetGroup] = {
    val insert = (groups.map(t => (t.entityId, t.name)) returning groups.map(_.id)) +=
      ((g.entityId.value, g.name))
    db.run(insert)
      .flatMap(newId => db.run(groups.filter(_.id === newId).result.head))
      .map(toDomain)
  }

  override def get(id: AssetGroupId): Future[Option[AssetGroup]] =
    db.run(groups.filter(_.id === id.value).result.headOption).map(_.map(toDomain))

  override def existsByEntityAndName(entityId: EntityId, name: String): Future[Boolean] =
    db.run(groups.filter(g => g.entityId === entityId.value && g.name === name.trim).exists.result)

  override def update(g: AssetGroup): Future[Option[AssetGroup]] = {
    val q = groups.filter(_.id === g.id.value).map(_.name).update(g.name)
    db.run(q).flatMap {
      case 0 => Future.successful(None)
      case _ => db.run(groups.filter(_.id === g.id.value).result.headOption).map(_.map(toDomain))
    }
  }

  override def listByEntity(entityId: EntityId): Future[Seq[AssetGroup]] =
    db.run(groups.filter(_.entityId === entityId.value).sortBy(_.id).result).map(_.map(toDomain))
}
