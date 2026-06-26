package todo.boot

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment, Mode}

import todo.task.application.TaskModule
import todo.category.application.CategoryModule
import todo.user.application.UserModule
import todo.shared.application.{Clock, SystemClock}
import todo.shared.infrastructure.{InMemoryPersistenceModule, SlickPersistenceModule}

class AppModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[Clock]).to(classOf[SystemClock])

    val useInMemory =
      environment.mode == Mode.Test ||
        configuration.getOptional[Boolean]("app.inMemory").getOrElse(false)
    install(if (useInMemory) new InMemoryPersistenceModule else new SlickPersistenceModule)

    install(new TaskModule)
    install(new CategoryModule)
    install(new UserModule)
  }
}
