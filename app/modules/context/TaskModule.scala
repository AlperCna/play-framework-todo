package modules.context

import com.google.inject.AbstractModule

import services.{TaskItemService, TaskItemServiceImpl}

/**
 * Task bounded-context'inin uygulama (service) wiring'i.
 *
 * Sadece servis port->impl eslesmesini tasir; repo port'lari ortama gore
 * persistence modullerinde (Slick/InMemory) baglanir. Yeni bir context = yeni bir
 * `*Module` + root [[modules.AppModule]]'de tek `install(...)` satiri; mevcut modullere
 * dokunulmaz (degisim entity/context basina izole).
 */
class TaskModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[TaskItemService]).to(classOf[TaskItemServiceImpl])
}
