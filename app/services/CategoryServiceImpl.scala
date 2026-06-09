package services

import javax.inject.{Inject, Singleton}

import domain.category.Category
import domain.common.DomainError
import repositories.CategoryRepository

/** [[CategoryService]]'in implementasyonu. */
@Singleton
class CategoryServiceImpl @Inject() (categoryRepo: CategoryRepository, clock: Clock)
    extends CategoryService {

  private val AuditUser = "system"

  override def list(): Seq[Category] = categoryRepo.list()

  override def listByUser(userId: Long): Seq[Category] = categoryRepo.listByUser(userId)

  override def get(id: Long): Option[Category] = categoryRepo.get(id)

  override def create(name: String, description: String, userId: Long): Either[DomainError, Category] =
    Category.create(name, description, userId, clock.now, AuditUser).map(categoryRepo.add)

  override def update(id: Long, name: String, description: String): Either[DomainError, Category] =
    for {
      category <- found(id)
      renamed <- category.rename(name)
      described <- renamed.setDescription(description)
      saved <- persist(described.markUpdated(clock.now, AuditUser))
    } yield saved

  override def rename(id: Long, name: String): Either[DomainError, Category] =
    for {
      category <- found(id)
      renamed <- category.rename(name)
      saved <- persist(renamed.markUpdated(clock.now, AuditUser))
    } yield saved

  override def changeDescription(id: Long, description: String): Either[DomainError, Category] =
    for {
      category <- found(id)
      changed <- category.setDescription(description)
      saved <- persist(changed.markUpdated(clock.now, AuditUser))
    } yield saved

  override def delete(id: Long): Either[DomainError, Category] =
    for {
      category <- found(id)
      saved <- persist(category.markDeleted(clock.now, AuditUser))
    } yield saved

  private def found(id: Long): Either[DomainError, Category] =
    categoryRepo.get(id).toRight(DomainError.NotFound("Category", id))

  private def persist(category: Category): Either[DomainError, Category] =
    categoryRepo.update(category).toRight(DomainError.NotFound("Category", category.id))
}
