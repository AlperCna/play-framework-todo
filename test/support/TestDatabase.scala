package support

import domain.category.Category
import domain.task.{TaskItem, TaskItemCategory}
import domain.user.User
import persistence.{Database, Table}

/** Testler icin BOS (seed'siz) bir in-memory Database; her test izole baslar. */
object TestDatabase {

  def empty(): Database = new Database {
    override val users: Table[User] = new Table[User]((u, id) => u.copy(id = id))
    override val tasks: Table[TaskItem] = new Table[TaskItem]((t, id) => t.copy(id = id))
    override val categories: Table[Category] = new Table[Category]((c, id) => c.copy(id = id))
    override val taskCategories: Table[TaskItemCategory] =
      new Table[TaskItemCategory]((tc, id) => tc.copy(id = id))
  }
}
