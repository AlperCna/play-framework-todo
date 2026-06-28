package drp.asset.application

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.domain.{AssetDomainError, Entity}
import drp.asset.infrastructure.{InMemoryEntityRepository, InMemoryExclusionRepository}

class ExclusionServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private def fixture = {
    val entities   = new InMemoryEntityRepository()
    val exclusions = new InMemoryExclusionRepository()
    val service    = new ExclusionServiceImpl(exclusions, entities)
    (entities, service)
  }

  private def newEntityId(entities: InMemoryEntityRepository): Long =
    entities.create(Entity.create("Akbank", "brand").toOption.get).futureValue.id

  "ExclusionService.register" should {

    "persist a valid exclusion under an existing entity and list it" in {
      val (entities, service) = fixture
      val id                  = newEntityId(entities)
      service.register(id, "akbankdirekt.com", "exact", "owned_unmonitored").futureValue shouldBe a[Right[_, _]]
      service.listByEntity(id).futureValue.map(_.value) should contain("akbankdirekt.com")
    }

    "reject a blank value and persist nothing" in {
      val (entities, service) = fixture
      val id                  = newEntityId(entities)
      service.register(id, "  ", "exact", "owned_unmonitored").futureValue shouldBe Left(AssetDomainError.BlankExclusionValue)
      service.listByEntity(id).futureValue shouldBe empty
    }

    "reject a blank reason and persist nothing" in {
      val (entities, service) = fixture
      val id                  = newEntityId(entities)
      service.register(id, "akbankdirekt.com", "exact", " ").futureValue shouldBe Left(AssetDomainError.BlankExclusionReason)
      service.listByEntity(id).futureValue shouldBe empty
    }

    "reject an exclusion under a non-existent entity" in {
      val (_, service) = fixture
      service.register(999L, "akbankdirekt.com", "exact", "owned_unmonitored").futureValue shouldBe
        Left(AssetDomainError.UnknownEntity(999L))
    }

    "reject a match type outside the allowed set" in {
      val (entities, service) = fixture
      val id                  = newEntityId(entities)
      service.register(id, "akbankdirekt.com", "wildcard", "owned_unmonitored").futureValue shouldBe
        Left(AssetDomainError.InvalidMatchType("wildcard"))
    }

    "reject an active duplicate (same entity, value, match type)" in {
      val (entities, service) = fixture
      val id                  = newEntityId(entities)
      service.register(id, "akbankdirekt.com", "exact", "owned_unmonitored").futureValue
      service.register(id, "akbankdirekt.com", "exact", "owned_unmonitored").futureValue shouldBe
        Left(AssetDomainError.DuplicateActiveExclusion(id, "akbankdirekt.com", "exact"))
      service.listByEntity(id).futureValue.size shouldBe 1
    }

    "accept an open reason value" in {
      val (entities, service) = fixture
      val id                  = newEntityId(entities)
      service.register(id, "third.example.com", "exact", "third_party_legit").futureValue shouldBe a[Right[_, _]]
    }

    "list only the target entity's exclusions" in {
      val (entities, service) = fixture
      val a                   = newEntityId(entities)
      val b                   = newEntityId(entities)
      service.register(a, "a.com", "exact", "manual").futureValue
      service.register(b, "b.com", "exact", "manual").futureValue
      service.listByEntity(a).futureValue.map(_.value) shouldBe Seq("a.com")
    }
  }
}
