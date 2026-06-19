package modules.persistence

import com.google.inject.AbstractModule

import repositories.interfaces._
import repositories.sql._

/**
 * Tum repository PORT'larini Slick (SQL Server) implementasyonlarina baglar
 * (dev/prod yolu).
 *
 * Backend SECIMI burada DEGIL: hangi persistence modulunun yuklenecegine root
 * [[modules.AppModule]] karar verir (`install(...)`). Bu modul yalnizca "Slick secildiyse
 * port->impl eslesmesi nedir" sorusunu yanitlar.
 *
 * YENI ENTITY: tek bir `bind(...)` satiri ekle. Liste entity basina izole
 * kalir (god-modul yok); `persistence.db.Tables` facade'iyle ayni felsefe.
 */
class SlickPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TaskItemRepository]).to(classOf[SlickTaskItemRepository])
    bind(classOf[UserRepository]).to(classOf[SlickUserRepository])
    bind(classOf[CategoryRepository]).to(classOf[SlickCategoryRepository])
    bind(classOf[TaskItemCategoryRepository]).to(classOf[SlickTaskItemCategoryRepository])
  }
}
