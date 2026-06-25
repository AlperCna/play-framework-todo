package todo.category.application

import com.google.inject.AbstractModule

class CategoryModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[CategoryService]).to(classOf[CategoryServiceImpl])
}
