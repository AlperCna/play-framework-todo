package drp.asset.infrastructure

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment, Mode}

import drp.asset.application.{EntityService, EntityServiceImpl}
import drp.asset.application.ports.EntityRepository
import drp.asset.infrastructure.inmemory.InMemoryEntityRepository
import drp.asset.infrastructure.slick.SlickEntityRepository

/**
 * Guice wiring for the DRP `asset` module. Installed by `drp.boot.DrpModule`.
 * Services bind to their impls; repositories bind to the Slick adapter, or the in-memory adapter
 * in Test mode / when `drp.inMemory=true` (DB-less service tests). More bindings are added per story.
 */
class AssetModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  private val useInMemory: Boolean =
    environment.mode == Mode.Test || configuration.getOptional[Boolean]("drp.inMemory").getOrElse(false)

  override def configure(): Unit = {
    bind(classOf[EntityService]).to(classOf[EntityServiceImpl])

    if (useInMemory) bind(classOf[EntityRepository]).to(classOf[InMemoryEntityRepository])
    else bind(classOf[EntityRepository]).to(classOf[SlickEntityRepository])
  }
}
