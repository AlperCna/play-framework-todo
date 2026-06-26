package drp.asset.application

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import drp.asset.application.ports.EntityRepository
import drp.asset.domain.{AssetDomainError, Entity}

@Singleton
class EntityServiceImpl @Inject() (repo: EntityRepository)(implicit ec: ExecutionContext)
    extends EntityService {

  override def register(name: String, entityType: String): Future[Either[AssetDomainError, Entity]] =
    Entity.create(name, entityType) match {
      case Left(error)   => Future.successful(Left(error))
      case Right(entity) => repo.create(entity).map(Right(_))
    }

  override def list(): Future[Seq[Entity]] = repo.listAll()
}
