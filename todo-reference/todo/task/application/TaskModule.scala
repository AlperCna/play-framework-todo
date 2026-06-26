package todo.task.application

import com.google.inject.AbstractModule

class TaskModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[TaskItemService]).to(classOf[TaskItemServiceImpl])
}
