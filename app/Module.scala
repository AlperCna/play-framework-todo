import com.google.inject.AbstractModule
import play.api.{Configuration, Environment, Mode}

import persistence.inmemory.{Database, InMemoryDatabase}
import repositories.interfaces._
import repositories.inmemory._
import repositories.sql._
import services._

/**
 * Uygulamanin bagimlilik enjeksiyonu (DI) yapilandirmasi.
 *
 * Play, `(environment, configuration)` alan bir `Module`'u otomatik yukler ve bu
 * iki argumani enjekte eder; boylece ORTAMA gore farkli baglama yapabiliriz:
 *   - Test modunda (veya `app.inMemory = true`): bellek-ici repo'lar + seed'li
 *     [[InMemoryDatabase]]. Testler gercek DB gerektirmez.
 *   - Dev/Prod: Slick (SQL Server) repo'lari. Bu yolda `Database`/`Table`/
 *     `InMemoryDatabase` HIC kullanilmaz; Slick repo'lari `DatabaseConfigProvider`
 *     ile dogrudan veritabanina konusur.
 *
 * Katmanlar arayuz->implementasyon olarak baglanir:
 *   Controller -> Service -> Repository -> (Slick | InMemory)
 */
class Module(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Clock]).to(classOf[SystemClock])

    // Servis (application) katmani — her ortamda ayni
    bind(classOf[TaskItemService]).to(classOf[TaskItemServiceImpl])
    bind(classOf[UserService]).to(classOf[UserServiceImpl])
    bind(classOf[CategoryService]).to(classOf[CategoryServiceImpl])

    val useInMemory =
      environment.mode == Mode.Test ||
        configuration.getOptional[Boolean]("app.inMemory").getOrElse(false)

    if (useInMemory) {
      bind(classOf[Database]).to(classOf[InMemoryDatabase])
      bind(classOf[TaskItemRepository]).to(classOf[InMemoryTaskItemRepository])
      bind(classOf[UserRepository]).to(classOf[InMemoryUserRepository])
      bind(classOf[CategoryRepository]).to(classOf[InMemoryCategoryRepository])
      bind(classOf[TaskItemCategoryRepository]).to(classOf[InMemoryTaskItemCategoryRepository])
    } else {
      bind(classOf[TaskItemRepository]).to(classOf[SlickTaskItemRepository])
      bind(classOf[UserRepository]).to(classOf[SlickUserRepository])
      bind(classOf[CategoryRepository]).to(classOf[SlickCategoryRepository])
      bind(classOf[TaskItemCategoryRepository]).to(classOf[SlickTaskItemCategoryRepository])
    }
  }
}
