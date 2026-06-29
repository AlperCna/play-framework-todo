package drp.asset.application

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.domain.{AssetId, AssetMetadata, Entity, EntityId}
import drp.asset.infrastructure.inmemory.{InMemoryAssetRepository, InMemoryEntityRepository}
import drp.shared.application.Clock
import drp.shared.domain.DomainError

class AssetServiceSpec extends AnyWordSpec with Matchers {

  private val clock: Clock = () => Instant.parse("2026-06-29T00:00:00Z")
  private def await[A](f: scala.concurrent.Future[A]): A = Await.result(f, 2.seconds)

  private def fixture = {
    val entityRepo = new InMemoryEntityRepository(clock)
    val assetRepo = new InMemoryAssetRepository(clock)
    val service = new AssetServiceImpl(entityRepo, assetRepo, clock)
    val entity = await(entityRepo.add(Entity.create("Akbank", "brand", clock.now()).toOption.get))
    (service, entity.id)
  }

  "AssetServiceImpl" should {

    "create an asset under an existing entity and list it" in {
      val (s, eid) = fixture
      await(s.create(eid, "domain", "akbank.com", AssetMetadata.empty)).isRight shouldBe true
      await(s.listByEntity(eid)).map(_.value) shouldBe Seq("akbank.com")
    }

    "reject a duplicate (entity, type, value) and write nothing" in {
      val (s, eid) = fixture
      await(s.create(eid, "domain", "akbank.com", AssetMetadata.empty))
      await(s.create(eid, "domain", "akbank.com", AssetMetadata.empty)) shouldBe
        Left(DomainError.DuplicateAsset(eid.value, "domain", "akbank.com"))
      await(s.listByEntity(eid)).size shouldBe 1
    }

    "reject an invalid/missing parent entity" in {
      val (s, _) = fixture
      await(s.create(EntityId(999L), "domain", "x.com", AssetMetadata.empty)) shouldBe
        Left(DomainError.EntityNotFound(999L))
    }

    "reject a blank value" in {
      val (s, eid) = fixture
      await(s.create(eid, "domain", "  ", AssetMetadata.empty)) shouldBe Left(DomainError.EmptyAssetValue)
    }

    "reject an unknown asset type" in {
      val (s, eid) = fixture
      await(s.create(eid, "ftp", "x.com", AssetMetadata.empty)) shouldBe Left(DomainError.InvalidAssetType("ftp"))
    }

    "return AssetNotFound when editing a missing asset" in {
      val (s, _) = fixture
      await(s.update(AssetId(999L), "domain", "x.com", AssetMetadata.empty)) shouldBe Left(DomainError.AssetNotFound(999L))
    }
  }
}
