package persistence.inmemory

import java.time.{Instant, LocalDate}
import javax.inject.Singleton

import domain.category.Category
import domain.common.Priority
import domain.task.{TaskItem, TaskItemCategory}
import domain.user.User

/**
 * [[Database]]'in bellek-ici, tekil (singleton) implementasyonu.
 *
 * `@Singleton`: Guice tek bir ornek olusturur; boylece tum repository'ler ayni
 * tablolar uzerinde calisir. Veriler uygulama belleginde durur, yeniden
 * baslatinca kaybolur.
 *
 * Acilista birkac ornek kayit (seed) eklenir ki UI bos olmasin. Seed verisi
 * bilinerek gecerli secildigi icin invariant ihlali bir PROGRAM hatasidir;
 * `getOrElse(sys.error(...))` ile acilista hizlica patlatiriz.
 */
@Singleton
class InMemoryDatabase extends Database {

  override val users: InMemoryTable[User] = new InMemoryTable[User]((u, id) => u.copy(id = id))
  override val tasks: InMemoryTable[TaskItem] = new InMemoryTable[TaskItem]((t, id) => t.copy(id = id))
  override val categories: InMemoryTable[Category] = new InMemoryTable[Category]((c, id) => c.copy(id = id))
  override val taskCategories: InMemoryTable[TaskItemCategory] =
    new InMemoryTable[TaskItemCategory]((tc, id) => tc.copy(id = id))

  // --- Seed (acilista calisir) ---
  // Altyapi koddur (domain degil), bu yuzden zamani burada uretmek serbest.
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

    // Bir adet ornek N-N iliski (UI'da gosterilmiyor; katmanin calistigini gosterir).
    taskCategories.add(
      TaskItemCategory
        .create(task1.id, workCategory.id, seedNow, SeedBy)
        .getOrElse(sys.error("Seed link gecersiz"))
    )
  }
}
