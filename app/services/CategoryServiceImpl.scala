package services

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

import domain.category.Category
import domain.common.DomainError
import repositories.interfaces.CategoryRepository

/** [[CategoryService]]'in implementasyonu. */
@Singleton
class CategoryServiceImpl @Inject() (categoryRepo: CategoryRepository, clock: Clock)(
    implicit ec: ExecutionContext
) extends CategoryService {

  private val AuditUser = "system"

  override def list(): Future[Seq[Category]] = categoryRepo.list()

  override def listByUser(userId: Long): Future[Seq[Category]] = categoryRepo.listByUser(userId)

  override def get(id: Long): Future[Option[Category]] = categoryRepo.get(id)

  override def create(
      name: String,
      description: String,
      userId: Long
  ): Future[Either[DomainError, Category]] =
    ServiceResult
      .fromEither(Category.create(name, description, userId, clock.now, AuditUser))
      .flatMap(category => ServiceResult.fromFuture(categoryRepo.add(category)))
      .value

  override def update(
      id: Long,
      name: String,
      description: String
  ): Future[Either[DomainError, Category]] =
    (for {
      category  <- found(id)
      renamed   <- ServiceResult.fromEither(category.rename(name))
      described <- ServiceResult.fromEither(renamed.setDescription(description))
      saved     <- persist(described.markUpdated(clock.now, AuditUser))
    } yield saved).value

  override def rename(id: Long, name: String): Future[Either[DomainError, Category]] =
    (for {
      category <- found(id)
      renamed  <- ServiceResult.fromEither(category.rename(name))
      saved    <- persist(renamed.markUpdated(clock.now, AuditUser))
    } yield saved).value

  override def changeDescription(id: Long, description: String): Future[Either[DomainError, Category]] =
    (for {
      category <- found(id)
      changed  <- ServiceResult.fromEither(category.setDescription(description))
      saved    <- persist(changed.markUpdated(clock.now, AuditUser))
    } yield saved).value

  override def delete(id: Long): Future[Either[DomainError, Category]] =
    (for {
      category <- found(id)
      saved    <- persist(category.markDeleted(clock.now, AuditUser))
    } yield saved).value

  private def found(id: Long): ServiceResult[Category] =
    ServiceResult.fromOptionF(categoryRepo.get(id), DomainError.NotFound("Category", id))

  private def persist(category: Category): ServiceResult[Category] =
    ServiceResult.fromOptionF(categoryRepo.update(category), DomainError.NotFound("Category", category.id))
}
