package drp.asset.application

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.domain.{Entity, EntityId}
import drp.asset.infrastructure.inmemory.{InMemoryEntityRepository, InMemoryExclusionRepository}
import drp.shared.application.Clock
import drp.shared.domain.DomainError

class ExclusionServiceSpec extends AnyWordSpec with Matchers {

  private val clock: Clock = () => Instant.parse("2026-06-29T00:00:00Z")
  private def await[A](f: scala.concurrent.Future[A]): A = Await.result(f, 2.seconds)

  private def fixture = {
    val entityRepo = new InMemoryEntityRepository(clock)
    val exclusionRepo = new InMemoryExclusionRepository(clock)
    val service = new ExclusionServiceImpl(entityRepo, exclusionRepo, clock)
    val entity = await(entityRepo.add(Entity.create("Akbank", "brand", clock.now()).toOption.get))
    (service, entity.id)
  }

  "ExclusionServiceImpl" should {

    "create an exclusion verbatim with createdBy=system and list it" in {
      val (s, eid) = fixture
      val created = await(s.create(eid, "akbankdirekt.com", "exact", "owned_unmonitored")).toOption.get
      created.value shouldBe "akbankdirekt.com"
      created.createdBy shouldBe "system"
      await(s.listActiveByEntity(eid)).map(_.value) shouldBe Seq("akbankdirekt.com")
    }

    "reject a duplicate (entity, value, match_type) and write nothing" in {
      val (s, eid) = fixture
      await(s.create(eid, "akbankdirekt.com", "exact", "manual"))
      await(s.create(eid, "akbankdirekt.com", "exact", "manual")) shouldBe
        Left(DomainError.DuplicateExclusion(eid.value, "akbankdirekt.com", "exact"))
      await(s.listActiveByEntity(eid)).size shouldBe 1
    }

    "reject a missing parent entity" in {
      val (s, _) = fixture
      await(s.create(EntityId(999L), "x.com", "exact", "manual")) shouldBe Left(DomainError.EntityNotFound(999L))
    }

    "reject a blank value" in {
      val (s, eid) = fixture
      await(s.create(eid, "  ", "exact", "manual")) shouldBe Left(DomainError.EmptyExclusionValue)
    }

    "reject an unknown match type" in {
      val (s, eid) = fixture
      await(s.create(eid, "x.com", "fuzzy", "manual")) shouldBe Left(DomainError.InvalidMatchType("fuzzy"))
    }
  }
}
