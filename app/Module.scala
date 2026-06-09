import com.google.inject.AbstractModule

import persistence.{Database, InMemoryDatabase}
import repositories._
import services._

/**
 * Uygulamanin bagimlilik enjeksiyonu (DI) yapilandirmasi.
 *
 * Play, `conf/application.conf` icinde aksi belirtilmedikce kok paketteki
 * `Module` sinifini otomatik olarak yukler.
 *
 * Katmanlar arayuz->implementasyon olarak baglanir:
 *   Controller -> Service -> Repository -> Database
 * Gercek bir DB'ye gecmek icin yalnizca `Database` baglamasini degistirmek yeter.
 */
class Module extends AbstractModule {
  override def configure(): Unit = {
    // Tek "DB" singleton'i ve saat portu
    bind(classOf[Database]).to(classOf[InMemoryDatabase])
    bind(classOf[Clock]).to(classOf[SystemClock])

    // Repository port'lari
    bind(classOf[TaskItemRepository]).to(classOf[InMemoryTaskItemRepository])
    bind(classOf[UserRepository]).to(classOf[InMemoryUserRepository])
    bind(classOf[CategoryRepository]).to(classOf[InMemoryCategoryRepository])
    bind(classOf[TaskItemCategoryRepository]).to(classOf[InMemoryTaskItemCategoryRepository])

    // Servis (application) katmani
    bind(classOf[TaskItemService]).to(classOf[TaskItemServiceImpl])
    bind(classOf[UserService]).to(classOf[UserServiceImpl])
    bind(classOf[CategoryService]).to(classOf[CategoryServiceImpl])
  }
}
