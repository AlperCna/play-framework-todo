package todo.task.application

import com.google.inject.AbstractModule

class CleanupModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[CompletedTaskCleanerScheduler]).asEagerSingleton()
}
