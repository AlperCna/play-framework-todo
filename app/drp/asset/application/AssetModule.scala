package drp.asset.application

import com.google.inject.AbstractModule

import drp.asset.application.ports.{EntityRepository, ExclusionRepository}
import drp.asset.infrastructure.{SlickEntityRepository, SlickExclusionRepository}

/**
 * Guice composition for the asset module. Binds the entity and exclusion services and their
 * repository ports to the PostgreSQL Slick adapters. Registered via `play.modules.enabled` in
 * application.conf.
 */
class AssetModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[EntityService]).to(classOf[EntityServiceImpl])
    bind(classOf[EntityRepository]).to(classOf[SlickEntityRepository])
    bind(classOf[ExclusionService]).to(classOf[ExclusionServiceImpl])
    bind(classOf[ExclusionRepository]).to(classOf[SlickExclusionRepository])
  }
}
