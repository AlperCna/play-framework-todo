package todo.user.application

import com.google.inject.AbstractModule

class UserModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[UserService]).to(classOf[UserServiceImpl])
}
