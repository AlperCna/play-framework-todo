package drp.asset.infrastructure

import com.google.inject.AbstractModule

/**
 * Guice wiring for the DRP `asset` module. Installed by `drp.boot.DrpModule`.
 *
 * Per-story tasks add bindings here as each user story lands:
 *   - service interface -> *Impl
 *   - repository port   -> Slick adapter (real) OR in-memory adapter (Test mode / `drp.inMemory=true`)
 * (the persistence-mode switch is introduced with the first repository binding in US1).
 */
class AssetModule extends AbstractModule {
  override def configure(): Unit = {
    // No bindings yet — added incrementally per user story (entity, asset, exclusion, asset group).
  }
}
