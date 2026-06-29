package drp.discovery.infrastructure

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment, Mode}


import drp.discovery.application.{DiscoveryIntakeService, DiscoveryIntakeServiceImpl}
import drp.discovery.application.ports.{DiscoveryRepository, PermutationProvider}
import drp.discovery.infrastructure.inmemory.InMemoryDiscoveryRepository
import drp.discovery.infrastructure.slick.SlickDiscoveryRepository

/**
 * Guice wiring for the DRP `discovery` module. Installed by `drp.boot.DrpModule`.
 * Uses the in-memory adapter in Test mode or when `drp.inMemory=true`.
 */
class DiscoveryModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  private val useInMemory: Boolean =
    environment.mode == Mode.Test || configuration.getOptional[Boolean]("drp.inMemory").getOrElse(false)

  override def configure(): Unit = {
    bind(classOf[DiscoveryIntakeService]).to(classOf[DiscoveryIntakeServiceImpl])

    if (useInMemory) {
      bind(classOf[DiscoveryRepository]).to(classOf[InMemoryDiscoveryRepository])
      bind(classOf[PermutationProvider])
        .toInstance(new FakePermutationProvider(FakePermutationProvider.defaultSeed))
    } else {
      bind(classOf[DiscoveryRepository]).to(classOf[SlickDiscoveryRepository])
      // Real permutation algorithm (dnstwist or similar) is a later story; stub is intentional for MVP.
      bind(classOf[PermutationProvider])
        .toInstance(new FakePermutationProvider(FakePermutationProvider.defaultSeed))
    }
  }
}
