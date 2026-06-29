package drp.asset.infrastructure.slick

import java.time.Instant

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.JsValue
import play.db.NamedDatabase

import drp.asset.application.ports.AssetRepository
import drp.asset.domain.{Asset, AssetGroupId, AssetId, AssetMetadata, AssetType, EntityId}
import drp.asset.infrastructure.AssetMetadataCodec
import drp.shared.infrastructure.MonaPgProfile

private[slick] final case class AssetRow(
    id: Long,
    entityId: Long,
    assetGroupId: Option[Long],
    assetType: String,
    value: String,
    metadata: JsValue,
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
)

/** Slick adapter for `AssetRepository` on the `drp` datasource. `metadata` is a JSONB column (slick-pg). */
@Singleton
class SlickAssetRepository @Inject() (
    @NamedDatabase("drp") protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends AssetRepository {

  private val dbConfig = dbConfigProvider.get[MonaPgProfile]
  import dbConfig.profile.api._
  private val db = dbConfig.db

  private class AssetsTable(tag: Tag) extends Table[AssetRow](tag, "assets") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def entityId = column[Long]("entity_id")
    def assetGroupId = column[Option[Long]]("asset_group_id")
    def assetType = column[String]("asset_type")
    def value = column[String]("value")
    def metadata = column[JsValue]("metadata")
    def isActive = column[Boolean]("is_active")
    def createdAt = column[Instant]("created_at")
    def updatedAt = column[Instant]("updated_at")
    def * = (id, entityId, assetGroupId, assetType, value, metadata, isActive, createdAt, updatedAt).mapTo[AssetRow]
  }

  private val assets = TableQuery[AssetsTable]

  private def toDomain(r: AssetRow): Asset =
    Asset(
      AssetId(r.id),
      EntityId(r.entityId),
      r.assetGroupId.map(AssetGroupId),
      AssetType.fromCode(r.assetType).getOrElse(AssetType.Domain), // DB CHECK guarantees a valid value
      r.value,
      AssetMetadataCodec.fromJson(r.metadata),
      r.isActive,
      r.createdAt,
      r.updatedAt
    )

  override def add(a: Asset): Future[Asset] = {
    val insert = (assets.map(t => (t.entityId, t.assetGroupId, t.assetType, t.value, t.metadata)) returning assets.map(_.id)) +=
      ((a.entityId.value, a.assetGroupId.map(_.value), AssetType.toCode(a.assetType), a.value, AssetMetadataCodec.toJson(a.metadata)))
    db.run(insert)
      .flatMap(newId => db.run(assets.filter(_.id === newId).result.head))
      .map(toDomain)
  }

  override def get(id: AssetId): Future[Option[Asset]] =
    db.run(assets.filter(_.id === id.value).result.headOption).map(_.map(toDomain))

  override def existsActive(entityId: EntityId, assetType: AssetType, value: String): Future[Boolean] =
    db.run(
      assets
        .filter(a => a.entityId === entityId.value && a.assetType === AssetType.toCode(assetType) && a.value === value.trim && a.isActive)
        .exists
        .result
    )

  override def update(a: Asset): Future[Option[Asset]] = {
    val q = assets.filter(_.id === a.id.value)
      .map(t => (t.assetType, t.value, t.metadata))
      .update((AssetType.toCode(a.assetType), a.value, AssetMetadataCodec.toJson(a.metadata)))
    db.run(q).flatMap {
      case 0 => Future.successful(None)
      case _ => db.run(assets.filter(_.id === a.id.value).result.headOption).map(_.map(toDomain))
    }
  }

  override def listByEntity(entityId: EntityId): Future[Seq[Asset]] =
    db.run(assets.filter(_.entityId === entityId.value).sortBy(_.id).result).map(_.map(toDomain))
}
