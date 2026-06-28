package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.{EntityRepository, ExclusionRepository}
import drp.asset.domain.{AssetDomainError, Exclusion}

@Singleton
class ExclusionServiceImpl @Inject() (
    exclusions: ExclusionRepository,
    entities: EntityRepository
)(implicit ec: ExecutionContext)
    extends ExclusionService {

  override def register(
      entityId: Long,
      value: String,
      matchType: String,
      reason: String
  ): Future[Either[AssetDomainError, Exclusion]] =
    Exclusion.create(entityId, value, matchType, reason) match {
      case Left(error) => Future.successful(Left(error))
      case Right(exclusion) =>
        entities.existsById(entityId).flatMap {
          case false => Future.successful(Left(AssetDomainError.UnknownEntity(entityId)))
          case true =>
            exclusions.existsActiveDuplicate(entityId, exclusion.value, exclusion.matchType).flatMap {
              case true =>
                Future.successful(
                  Left(AssetDomainError.DuplicateActiveExclusion(entityId, exclusion.value, exclusion.matchType.asValue))
                )
              case false => exclusions.create(exclusion).map(Right(_))
            }
        }
    }

  override def listByEntity(entityId: Long): Future[Seq[Exclusion]] =
    exclusions.listByEntity(entityId)
}
