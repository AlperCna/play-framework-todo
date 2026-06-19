package modules.context

import com.google.inject.AbstractModule

import services.{CategoryService, CategoryServiceImpl}

/**
 * Category bounded-context'inin uygulama (service) wiring'i.
 * Bkz. [[TaskModule]] — ayni desen.
 */
class CategoryModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[CategoryService]).to(classOf[CategoryServiceImpl])
}
