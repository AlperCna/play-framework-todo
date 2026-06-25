package support

import todo.category.domain.Category
import todo.shared.infrastructure.Database
import todo.shared.infrastructure.InMemoryTable
import todo.task.domain.{TaskItem, TaskItemCategory}
import todo.user.domain.User

object TestDatabase {

  def empty(): Database = new Database {
    override val users: InMemoryTable[User] = new InMemoryTable[User]((u, id) => u.copy(id = id))
    override val tasks: InMemoryTable[TaskItem] = new InMemoryTable[TaskItem]((t, id) => t.copy(id = id))
    override val categories: InMemoryTable[Category] = new InMemoryTable[Category]((c, id) => c.copy(id = id))
    override val taskCategories: InMemoryTable[TaskItemCategory] =
      new InMemoryTable[TaskItemCategory]((tc, id) => tc.copy(id = id))
  }
}
