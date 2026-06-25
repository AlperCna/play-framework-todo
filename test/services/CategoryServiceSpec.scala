package services

import java.time.{Instant, LocalDate}

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import todo.category.application.CategoryServiceImpl
import todo.category.infrastructure.InMemoryCategoryRepository
import todo.shared.domain.DomainError
import support.{FixedClock, TestDatabase}

class CategoryServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val today = LocalDate.of(2026, 6, 8)

  private def newService() = {
    val db = TestDatabase.empty()
    val repo = new InMemoryCategoryRepository(db)
    (new CategoryServiceImpl(repo, FixedClock(now, today)), repo)
  }

  "CategoryService.create" should {
    "kategoriyi kaydetmeli ve audit createdBy='system' olmali" in {
      val (service, repo) = newService()
      val created = service.create("Is", "Ise dair", userId = 1L).futureValue
      created.map(_.audit.createdBy) shouldBe Right("system")
      repo.list().futureValue should have size 1
    }
    "bos aciklamayi reddetmeli (domain)" in {
      val (service, _) = newService()
      service.create("Is", "  ", 1L).futureValue shouldBe Left(DomainError.EmptyCategoryDescription)
    }
  }

  "CategoryService.update" should {
    "ad ve aciklamayi birlikte degistirmeli" in {
      val (service, _) = newService()
      val c = service.create("Eski", "eski aciklama", 1L).futureValue.getOrElse(fail())
      val updated = service.update(c.id, "Yeni", "yeni aciklama").futureValue.getOrElse(fail())
      updated.name shouldBe "Yeni"
      updated.description shouldBe "yeni aciklama"
    }
    "olmayan id'de NotFound dondurmeli" in {
      val (service, _) = newService()
      service.update(999L, "x", "y").futureValue shouldBe Left(DomainError.NotFound("Category", 999L))
    }
  }

  "CategoryService.delete" should {
    "soft-delete sonrasi list() gostermemeli" in {
      val (service, _) = newService()
      val c = service.create("Is", "aciklama", 1L).futureValue.getOrElse(fail())
      service.delete(c.id).futureValue.isRight shouldBe true
      service.list().futureValue.map(_.id) should not contain c.id
    }
  }
}
