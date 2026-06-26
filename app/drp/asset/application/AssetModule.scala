package drp.asset.application

import com.google.inject.AbstractModule

import drp.asset.application.ports.EntityRepository
import drp.asset.infrastructure.SlickEntityRepository

/**
 * Guice composition for the asset module. Binds the entity service and its repository port
 * to the PostgreSQL Slick adapter. Registered via `play.modules.enabled` in application.conf.
 */
class AssetModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[EntityService]).to(classOf[EntityServiceImpl])
    bind(classOf[EntityRepository]).to(classOf[SlickEntityRepository])
  }
}
