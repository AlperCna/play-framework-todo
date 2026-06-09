package services

import java.time.{Instant, LocalDate}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import domain.common.{DomainError, Priority}
import repositories.{
  InMemoryCategoryRepository,
  InMemoryTaskItemCategoryRepository,
  InMemoryTaskItemRepository
}
import support.{FixedClock, TestDatabase}

/**
 * TaskItemService: orkestrasyonun ve yan etki sinirinin (Clock, audit "system")
 * uctan uca dogru calistigini, sabit saatle deterministik biçimde dogrular.
 */
class TaskItemServiceSpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val today = LocalDate.of(2026, 6, 8)

  private def newService(clock: FixedClock) = {
    val db = TestDatabase.empty()
    val taskRepo = new InMemoryTaskItemRepository(db)
    val service = new TaskItemServiceImpl(
      taskRepo,
      new InMemoryCategoryRepository(db),
      new InMemoryTaskItemCategoryRepository(db),
      clock
    )
    (service, taskRepo)
  }

  "TaskItemService.create" should {
    "gorevi repo'ya kaydetmeli ve audit createdBy='system' olmali" in {
      val (service, taskRepo) = newService(FixedClock(now, today))
      val created = service.create("Gorev", None, Priority.Low, None, userId = 1L)
      created.map(_.id) shouldBe Right(1L)
      created.map(_.audit.createdBy) shouldBe Right("system")
      taskRepo.list() should have size 1
    }
  }

  "TaskItemService.complete" should {
    "son tarihi gecmis gorevde Left dondurmeli ve repo'yu degistirmemeli" in {
      val (service, taskRepo) = newService(FixedClock(now, today))
      val task = service
        .create("Gorev", None, Priority.Low, Some(today.minusDays(1)), userId = 1L)
        .getOrElse(fail())

      service.complete(task.id) shouldBe Left(DomainError.TaskPastDueCannotComplete)
      taskRepo.get(task.id).map(_.isCompleted) shouldBe Some(false)
    }
  }

  "TaskItemService.delete" should {
    "soft-delete sonrasi list() gorevi gostermemeli" in {
      val (service, taskRepo) = newService(FixedClock(now, today))
      val task = service.create("Gorev", None, Priority.Low, None, userId = 1L).getOrElse(fail())

      service.delete(task.id).isRight shouldBe true
      taskRepo.list().map(_.id) should not contain task.id
    }
  }

  "TaskItemService.update" should {
    "olmayan id'de NotFound dondurmeli" in {
      val (service, _) = newService(FixedClock(now, today))
      service.update(999L, "x", None, Priority.Low, None) shouldBe
        Left(DomainError.NotFound("TaskItem", 999L))
    }
  }
}
