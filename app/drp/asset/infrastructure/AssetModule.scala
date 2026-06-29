package drp.asset.infrastructure

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment, Mode}

import drp.asset.application.{
  AssetGroupService,
  AssetGroupServiceImpl,
  AssetReadPortImpl,
  AssetService,
  AssetServiceImpl,
  EntityService,
  EntityServiceImpl,
  ExclusionService,
  ExclusionServiceImpl
}
import drp.asset.application.ports.{
  AssetGroupRepository,
  AssetReadPort,
  AssetRepository,
  EntityRepository,
  ExclusionRepository
}
import drp.asset.infrastructure.inmemory.{
  InMemoryAssetGroupRepository,
  InMemoryAssetRepository,
  InMemoryEntityRepository,
  InMemoryExclusionRepository
}
import drp.asset.infrastructure.slick.{
  SlickAssetGroupRepository,
  SlickAssetRepository,
  SlickEntityRepository,
  SlickExclusionRepository
}

/**
 * Guice wiring for the DRP `asset` module. Installed by `drp.boot.DrpModule`.
 * Services bind to their impls; repositories bind to the Slick adapter, or the in-memory adapter
 * in Test mode / when `drp.inMemory=true` (DB-less service tests).
 */
class AssetModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  private val useInMemory: Boolean =
    environment.mode == Mode.Test || configuration.getOptional[Boolean]("drp.inMemory").getOrElse(false)

  override def configure(): Unit = {
    bind(classOf[EntityService]).to(classOf[EntityServiceImpl])
    bind(classOf[AssetService]).to(classOf[AssetServiceImpl])
    bind(classOf[ExclusionService]).to(classOf[ExclusionServiceImpl])
    bind(classOf[AssetGroupService]).to(classOf[AssetGroupServiceImpl])
    bind(classOf[AssetReadPort]).to(classOf[AssetReadPortImpl])

    if (useInMemory) {
      bind(classOf[EntityRepository]).to(classOf[InMemoryEntityRepository])
      bind(classOf[AssetRepository]).to(classOf[InMemoryAssetRepository])
      bind(classOf[ExclusionRepository]).to(classOf[InMemoryExclusionRepository])
      bind(classOf[AssetGroupRepository]).to(classOf[InMemoryAssetGroupRepository])
    } else {
      bind(classOf[EntityRepository]).to(classOf[SlickEntityRepository])
      bind(classOf[AssetRepository]).to(classOf[SlickAssetRepository])
      bind(classOf[ExclusionRepository]).to(classOf[SlickExclusionRepository])
      bind(classOf[AssetGroupRepository]).to(classOf[SlickAssetGroupRepository])
    }
  }
}
