package todo.shared.infrastructure

import todo.category.domain.Category
import todo.task.domain.{TaskItem, TaskItemCategory}
import todo.user.domain.User

trait Database {
  def users: InMemoryTable[User]
  def tasks: InMemoryTable[TaskItem]
  def categories: InMemoryTable[Category]
  def taskCategories: InMemoryTable[TaskItemCategory]
}
