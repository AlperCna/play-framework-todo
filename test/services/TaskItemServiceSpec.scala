package services

import java.time.{Instant, LocalDate}

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import domain.common.{DomainError, Priority}
import repositories.inmemory.{
  InMemoryCategoryRepository,
  InMemoryTaskItemCategoryRepository,
  InMemoryTaskItemRepository
}
import support.{FixedClock, TestDatabase}

/**
 * TaskItemService: orkestrasyonun ve yan etki sinirinin (Clock, audit "system")
 * uctan uca dogru calistigini, sabit saatle deterministik biçimde dogrular.
 *
 * Servisler artik `Future` dondugu icin sonuclar `futureValue` ile beklenir
 * (bellek-ici repo `Future.successful` dondurdugu icin aninda tamamlanir).
 */
class TaskItemServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val today = LocalDate.of(2026, 6, 8)

  /** Ayni in-memory DB'yi paylasan TaskItem + Category servisleri ve task repo'su. */
  private def newService(clock: FixedClock) = {
    val db = TestDatabase.empty()
    val taskRepo = new InMemoryTaskItemRepository(db)
    val catRepo = new InMemoryCategoryRepository(db)
    val service = new TaskItemServiceImpl(
      taskRepo,
      catRepo,
      new InMemoryTaskItemCategoryRepository(db),
      clock
    )
    val categoryService = new CategoryServiceImpl(catRepo, clock)
    (service, categoryService, taskRepo)
  }

  "TaskItemService.create" should {
    "gorevi repo'ya kaydetmeli ve audit createdBy='system' olmali" in {
      val (service, _, taskRepo) = newService(FixedClock(now, today))
      val created = service.create("Gorev", None, Priority.Low, None, userId = 1L).futureValue
      created.map(_.id) shouldBe Right(1L)
      created.map(_.audit.createdBy) shouldBe Right("system")
      taskRepo.list().futureValue should have size 1
    }
  }

  "TaskItemService.complete" should {
    "son tarihi gecmis gorevde Left dondurmeli ve repo'yu degistirmemeli" in {
      val (service, _, taskRepo) = newService(FixedClock(now, today))
      val task = service
        .create("Gorev", None, Priority.Low, Some(today.minusDays(1)), userId = 1L)
        .futureValue
        .getOrElse(fail())

      service.complete(task.id).futureValue shouldBe Left(DomainError.TaskPastDueCannotComplete)
      taskRepo.get(task.id).futureValue.map(_.isCompleted) shouldBe Some(false)
    }
  }

  "TaskItemService.delete" should {
    "soft-delete sonrasi list() gorevi gostermemeli" in {
      val (service, _, taskRepo) = newService(FixedClock(now, today))
      val task =
        service.create("Gorev", None, Priority.Low, None, userId = 1L).futureValue.getOrElse(fail())

      service.delete(task.id).futureValue.isRight shouldBe true
      taskRepo.list().futureValue.map(_.id) should not contain task.id
    }
  }

  "TaskItemService.update" should {
    "olmayan id'de NotFound dondurmeli" in {
      val (service, _, _) = newService(FixedClock(now, today))
      service.update(999L, "x", None, Priority.Low, None).futureValue shouldBe
        Left(DomainError.NotFound("TaskItem", 999L))
    }
  }

  "TaskItemService kategori atama" should {

    "assignToCategory yeni link olusturmali ve categoriesOf'ta gorunmeli" in {
      val (service, categoryService, _) = newService(FixedClock(now, today))
      val task = service.create("Gorev", None, Priority.Low, None, 1L).futureValue.getOrElse(fail())
      val cat = categoryService.create("Is", "aciklama", 1L).futureValue.getOrElse(fail())

      service.assignToCategory(task.id, cat.id).futureValue.map(_.isDefined) shouldBe Right(true)
      service.categoriesOf(task.id).futureValue.map(_.id) should contain(cat.id)
    }

    "ayni kategoriye ikinci atama idempotent olmali (Right(None))" in {
      val (service, categoryService, _) = newService(FixedClock(now, today))
      val task = service.create("Gorev", None, Priority.Low, None, 1L).futureValue.getOrElse(fail())
      val cat = categoryService.create("Is", "aciklama", 1L).futureValue.getOrElse(fail())

      service.assignToCategory(task.id, cat.id).futureValue
      service.assignToCategory(task.id, cat.id).futureValue shouldBe Right(None)
      service.categoriesOf(task.id).futureValue should have size 1
    }

    "silinmis kategoriye atama basarisiz olmali (kategori artik getirilemez)" in {
      val (service, categoryService, _) = newService(FixedClock(now, today))
      val task = service.create("Gorev", None, Priority.Low, None, 1L).futureValue.getOrElse(fail())
      val cat = categoryService.create("Is", "aciklama", 1L).futureValue.getOrElse(fail())
      categoryService.delete(cat.id).futureValue

      service.assignToCategory(task.id, cat.id).futureValue shouldBe
        Left(DomainError.NotFound("Category", cat.id))
    }

    "removeFromCategory sonrasi categoriesOf ilgili kategoriyi gostermemeli" in {
      val (service, categoryService, _) = newService(FixedClock(now, today))
      val task = service.create("Gorev", None, Priority.Low, None, 1L).futureValue.getOrElse(fail())
      val cat = categoryService.create("Is", "aciklama", 1L).futureValue.getOrElse(fail())
      service.assignToCategory(task.id, cat.id).futureValue

      service.removeFromCategory(task.id, cat.id).futureValue.isRight shouldBe true
      service.categoriesOf(task.id).futureValue.map(_.id) should not contain cat.id
    }
  }
}
