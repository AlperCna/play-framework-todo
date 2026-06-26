package todo.shared.infrastructure

import com.google.inject.AbstractModule

import todo.task.application.{TaskItemRepository, TaskItemCategoryRepository}
import todo.task.infrastructure.{SlickTaskItemRepository, SlickTaskItemCategoryRepository}
import todo.category.application.CategoryRepository
import todo.category.infrastructure.SlickCategoryRepository
import todo.user.application.UserRepository
import todo.user.infrastructure.SlickUserRepository

class SlickPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TaskItemRepository]).to(classOf[SlickTaskItemRepository])
    bind(classOf[UserRepository]).to(classOf[SlickUserRepository])
    bind(classOf[CategoryRepository]).to(classOf[SlickCategoryRepository])
    bind(classOf[TaskItemCategoryRepository]).to(classOf[SlickTaskItemCategoryRepository])
  }
}
