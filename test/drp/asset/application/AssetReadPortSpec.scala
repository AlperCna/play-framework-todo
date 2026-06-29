package drp.asset.application

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.application.ports.ExclusionView
import drp.asset.domain.{Entity, EntityId}
import drp.asset.infrastructure.inmemory.{InMemoryAssetGroupRepository, InMemoryAssetRepository, InMemoryEntityRepository, InMemoryExclusionRepository}
import drp.shared.application.Clock

class AssetReadPortSpec extends AnyWordSpec with Matchers {

  private val clock: Clock = () => Instant.parse("2026-06-29T00:00:00Z")
  private def await[A](f: scala.concurrent.Future[A]): A = Await.result(f, 2.seconds)

  private def fixture = {
    val entityRepo = new InMemoryEntityRepository(clock)
    val assetRepo = new InMemoryAssetRepository(clock)
    val exclusionRepo = new InMemoryExclusionRepository(clock)
    val groupRepo = new InMemoryAssetGroupRepository(clock)
    val entitySvc = new EntityServiceImpl(entityRepo, clock)
    val assetSvc = new AssetServiceImpl(entityRepo, assetRepo, groupRepo, clock)
    val exclusionSvc = new ExclusionServiceImpl(entityRepo, exclusionRepo, clock)
    val readPort = new AssetReadPortImpl(entityRepo, assetRepo, exclusionRepo)
    val eid = await(entitySvc.create("Akbank", "brand")).toOption.get.id
    (assetSvc, exclusionSvc, readPort, eid)
  }

  "AssetReadPort" should {

    "return exactly the entity's active exclusions as read-models (no matching)" in {
      import drp.asset.domain.AssetMetadata
      val (assetSvc, exclusionSvc, readPort, eid) = fixture
      await(assetSvc.create(eid, "domain", "akbank.com", AssetMetadata.empty))
      await(exclusionSvc.create(eid, "akbankdirekt.com", "exact", "owned_unmonitored"))
      await(readPort.activeExclusions(eid)) shouldBe Seq(ExclusionView("akbankdirekt.com", "exact", "owned_unmonitored"))
    }

    "resolve an entity together with its assets" in {
      import drp.asset.domain.AssetMetadata
      val (assetSvc, _, readPort, eid) = fixture
      await(assetSvc.create(eid, "domain", "akbank.com", AssetMetadata.empty))
      val resolved = await(readPort.resolveEntityWithAssets(eid))
      resolved.map(_.entity.name) shouldBe Some("Akbank")
      resolved.map(_.assets.map(_.value)) shouldBe Some(Seq("akbank.com"))
    }

    "return None for an unknown entity" in {
      val (_, _, readPort, _) = fixture
      await(readPort.resolveEntityWithAssets(EntityId(999L))) shouldBe None
    }
  }
}
