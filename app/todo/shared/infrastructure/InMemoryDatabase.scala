package todo.shared.infrastructure

import java.time.{Instant, LocalDate}
import javax.inject.Singleton

import todo.category.domain.Category
import todo.task.domain.{TaskItem, TaskItemCategory}
import todo.user.domain.User
import todo.shared.domain.Priority

@Singleton
class InMemoryDatabase extends Database {

  override val users: InMemoryTable[User] = new InMemoryTable[User]((u, id) => u.copy(id = id))
  override val tasks: InMemoryTable[TaskItem] = new InMemoryTable[TaskItem]((t, id) => t.copy(id = id))
  override val categories: InMemoryTable[Category] = new InMemoryTable[Category]((c, id) => c.copy(id = id))
  override val taskCategories: InMemoryTable[TaskItemCategory] =
    new InMemoryTable[TaskItemCategory]((tc, id) => tc.copy(id = id))

  private val seedNow: Instant = Instant.now()
  private val seedToday: LocalDate = LocalDate.now()
  private val SeedBy = "system"

  locally {
    val demoUser = users.add(
      User
        .create("demo@example.com", "demo123", seedNow, SeedBy)
        .getOrElse(sys.error("Seed user gecersiz"))
    )

    val workCategory = categories.add(
      Category
        .create("Is", "Ise dair gorevler", demoUser.id, seedNow, SeedBy)
        .getOrElse(sys.error("Seed category gecersiz"))
    )

    val task1 = tasks.add(
      TaskItem
        .create("Play framework ogren", None, Priority.Medium, None, demoUser.id, seedNow, SeedBy)
        .getOrElse(sys.error("Seed task-1 gecersiz"))
    )

    tasks.add(
      TaskItem
        .create(
          "Domain modeli tasarla",
          Some("Rich domain model + repository + service"),
          Priority.High,
          Some(seedToday.plusDays(7)),
          demoUser.id,
          seedNow,
          SeedBy
        )
        .getOrElse(sys.error("Seed task-2 gecersiz"))
    )

    taskCategories.add(
      TaskItemCategory
        .create(task1.id, workCategory.id, seedNow, SeedBy)
        .getOrElse(sys.error("Seed link gecersiz"))
    )
  }
}
