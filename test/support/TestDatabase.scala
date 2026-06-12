package support

import domain.category.Category
import domain.task.{TaskItem, TaskItemCategory}
import domain.user.User
import persistence.inmemory.{Database, InMemoryTable}

/** Testler icin BOS (seed'siz) bir in-memory Database; her test izole baslar. */
object TestDatabase {

  def empty(): Database = new Database {
    override val users: InMemoryTable[User] = new InMemoryTable[User]((u, id) => u.copy(id = id))
    override val tasks: InMemoryTable[TaskItem] = new InMemoryTable[TaskItem]((t, id) => t.copy(id = id))
    override val categories: InMemoryTable[Category] = new InMemoryTable[Category]((c, id) => c.copy(id = id))
    override val taskCategories: InMemoryTable[TaskItemCategory] =
      new InMemoryTable[TaskItemCategory]((tc, id) => tc.copy(id = id))
  }
}
