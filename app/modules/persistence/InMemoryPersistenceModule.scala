package modules.persistence

import com.google.inject.AbstractModule

import persistence.inmemory.{Database, InMemoryDatabase}
import repositories.inmemory._
import repositories.interfaces._

/**
 * Tum repository PORT'larini bellek-ici implementasyonlara baglar (test modu ya da
 * `app.inMemory = true` ile DB'siz dev).
 *
 * Slick'ten farkli olarak burada ayrica tekil [[persistence.inmemory.Database]]
 * de baglanir; tum in-memory repo'lar bu ortak depoya delege eder.
 *
 * Backend SECIMI burada DEGIL: root [[modules.AppModule]] hangi persistence modulunu
 * `install(...)` edecegine karar verir.
 */
class InMemoryPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Database]).to(classOf[InMemoryDatabase])
    bind(classOf[TaskItemRepository]).to(classOf[InMemoryTaskItemRepository])
    bind(classOf[UserRepository]).to(classOf[InMemoryUserRepository])
    bind(classOf[CategoryRepository]).to(classOf[InMemoryCategoryRepository])
    bind(classOf[TaskItemCategoryRepository]).to(classOf[InMemoryTaskItemCategoryRepository])
  }
}
