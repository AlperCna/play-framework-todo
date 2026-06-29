package drp.asset.application

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.domain.{AssetMetadata, Entity, EntityId}
import drp.asset.infrastructure.inmemory.{InMemoryAssetGroupRepository, InMemoryAssetRepository, InMemoryEntityRepository}
import drp.shared.application.Clock
import drp.shared.domain.DomainError

class AssetGroupServiceSpec extends AnyWordSpec with Matchers {

  private val clock: Clock = () => Instant.parse("2026-06-29T00:00:00Z")
  private def await[A](f: scala.concurrent.Future[A]): A = Await.result(f, 2.seconds)

  private def fixture = {
    val entityRepo = new InMemoryEntityRepository(clock)
    val groupRepo = new InMemoryAssetGroupRepository(clock)
    val assetRepo = new InMemoryAssetRepository(clock)
    val groupSvc = new AssetGroupServiceImpl(entityRepo, groupRepo, clock)
    val assetSvc = new AssetServiceImpl(entityRepo, assetRepo, groupRepo, clock)
    val e1 = await(entityRepo.add(Entity.create("Akbank", "brand", clock.now()).toOption.get)).id
    val e2 = await(entityRepo.add(Entity.create("Garanti", "brand", clock.now()).toOption.get)).id
    (groupSvc, assetSvc, e1, e2)
  }

  "AssetGroupServiceImpl" should {

    "create a group under an existing entity and list it" in {
      val (gs, _, e1, _) = fixture
      await(gs.create(e1, "Akbank Direkt")).isRight shouldBe true
      await(gs.listByEntity(e1)).map(_.name) shouldBe Seq("Akbank Direkt")
    }

    "reject a duplicate group name within the entity" in {
      val (gs, _, e1, _) = fixture
      await(gs.create(e1, "Akbank Direkt"))
      await(gs.create(e1, "akbank direkt")) shouldBe Left(DomainError.DuplicateAssetGroupName(e1.value, "akbank direkt"))
    }

    "reject a missing parent entity" in {
      val (gs, _, _, _) = fixture
      await(gs.create(EntityId(999L), "X")) shouldBe Left(DomainError.EntityNotFound(999L))
    }
  }

  "AssetService group assignment (FR-005)" should {

    "allow assigning an asset to a same-entity group" in {
      val (gs, as, e1, _) = fixture
      val g = await(gs.create(e1, "Akbank Direkt")).toOption.get
      await(as.create(e1, "domain", "akbank.com", AssetMetadata.empty, Some(g.id))).isRight shouldBe true
    }

    "prevent assigning an asset to a group from a different entity" in {
      val (gs, as, e1, e2) = fixture
      val g1 = await(gs.create(e1, "Akbank Direkt")).toOption.get
      await(as.create(e2, "domain", "garanti.com", AssetMetadata.empty, Some(g1.id))) shouldBe
        Left(DomainError.AssetGroupEntityMismatch(e1.value, e2.value))
    }
  }
}
