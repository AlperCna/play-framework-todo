package modules.context

import com.google.inject.AbstractModule

import services.{UserService, UserServiceImpl}

/**
 * User bounded-context'inin uygulama (service) wiring'i.
 * Bkz. [[TaskModule]] — ayni desen.
 */
class UserModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[UserService]).to(classOf[UserServiceImpl])
}
