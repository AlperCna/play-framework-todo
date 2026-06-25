package todo.shared.infrastructure

import com.google.inject.AbstractModule

import todo.task.application.{TaskItemRepository, TaskItemCategoryRepository}
import todo.task.infrastructure.{InMemoryTaskItemRepository, InMemoryTaskItemCategoryRepository}
import todo.category.application.CategoryRepository
import todo.category.infrastructure.InMemoryCategoryRepository
import todo.user.application.UserRepository
import todo.user.infrastructure.InMemoryUserRepository

class InMemoryPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Database]).to(classOf[InMemoryDatabase])
    bind(classOf[TaskItemRepository]).to(classOf[InMemoryTaskItemRepository])
    bind(classOf[UserRepository]).to(classOf[InMemoryUserRepository])
    bind(classOf[CategoryRepository]).to(classOf[InMemoryCategoryRepository])
    bind(classOf[TaskItemCategoryRepository]).to(classOf[InMemoryTaskItemCategoryRepository])
  }
}
