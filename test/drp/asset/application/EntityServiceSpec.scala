package drp.asset.application

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.domain.EntityId
import drp.asset.infrastructure.inmemory.InMemoryEntityRepository
import drp.shared.application.{Clock, PageRequest}
import drp.shared.domain.DomainError

class EntityServiceSpec extends AnyWordSpec with Matchers {

  private val fixedClock: Clock = () => Instant.parse("2026-06-29T00:00:00Z")
  private def newService = new EntityServiceImpl(new InMemoryEntityRepository(fixedClock), fixedClock)
  private def await[A](f: scala.concurrent.Future[A]): A = Await.result(f, 2.seconds)

  "EntityServiceImpl" should {

    "create an entity and show it in the list" in {
      val s = newService
      await(s.create("Akbank", "brand")).isRight shouldBe true
      await(s.list(PageRequest.of(1))).items.map(_.name) shouldBe Seq("Akbank")
    }

    "reject a duplicate name (case-insensitive) and write nothing" in {
      val s = newService
      await(s.create("Akbank", "brand"))
      await(s.create("akbank", "brand")) shouldBe Left(DomainError.DuplicateEntityName("akbank"))
      await(s.list(PageRequest.of(1))).total shouldBe 1L
    }

    "reject a blank name" in {
      val s = newService
      await(s.create("   ", "brand")) shouldBe Left(DomainError.EmptyEntityName)
      await(s.list(PageRequest.of(1))).total shouldBe 0L
    }

    "edit name/type and keep createdAt" in {
      val s = newService
      val created = await(s.create("Akbank", "brand")).toOption.get
      val updated = await(s.update(created.id, "Akbank TR", "institution")).toOption.get
      updated.name shouldBe "Akbank TR"
      updated.entityType.code shouldBe "institution"
      updated.createdAt shouldBe created.createdAt
    }

    "return EntityNotFound when editing a missing entity" in {
      val s = newService
      await(s.update(EntityId(999L), "X", "brand")) shouldBe Left(DomainError.EntityNotFound(999L))
    }
  }
}
