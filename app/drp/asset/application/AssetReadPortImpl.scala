package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports._
import drp.asset.domain.EntityId

/**
 * Read-seam implementation built on the module's repository ports (so it works with either the Slick
 * or in-memory backend). Maps domain rows to read-models; applies NO matching logic.
 */
@Singleton
class AssetReadPortImpl @Inject() (
    entityRepo: EntityRepository,
    assetRepo: AssetRepository,
    exclusionRepo: ExclusionRepository
)(implicit ec: ExecutionContext)
    extends AssetReadPort {

  override def activeExclusions(entityId: EntityId): Future[Seq[ExclusionView]] =
    exclusionRepo.listActiveByEntity(entityId).map(_.map(x => ExclusionView(x.value, x.matchType.code, x.reason.code)))

  override def resolveEntityWithAssets(entityId: EntityId): Future[Option[EntityWithAssets]] =
    entityRepo.get(entityId).flatMap {
      case None => Future.successful(None)
      case Some(e) =>
        assetRepo.listByEntity(entityId).map { assets =>
          Some(EntityWithAssets(
            EntityView(e.id.value, e.name, e.entityType.code),
            assets.map(a => AssetView(a.id.value, a.assetType.code, a.value))
          ))
        }
    }
}
