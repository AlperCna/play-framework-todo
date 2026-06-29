package drp.boot

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}

import drp.shared.application.{Clock, SystemClock}
import drp.asset.infrastructure.AssetModule

/**
 * Root Guice module for the DRP modular monolith — binds shared services and composes each DRP module.
 * Registered in conf/application.conf (`play.modules.enabled += "drp.boot.DrpModule"`). Independent of
 * `app/todo` (Constitution I). Play injects (Environment, Configuration).
 */
class DrpModule(environment: Environment, configuration: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Clock]).to(classOf[SystemClock])
    install(new AssetModule(environment, configuration))
  }
}
