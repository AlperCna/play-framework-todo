package domain

import java.time.{Instant, LocalDate}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import todo.category.domain.Category
import todo.shared.domain.DomainError
import todo.shared.domain.Priority
import todo.task.domain.{TaskItem, TaskItemCategory}

class TaskItemSpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val today = LocalDate.of(2026, 6, 8)
  private val by = "tester"

  private def validTask(
      priority: Priority = Priority.Medium,
      dueDate: Option[LocalDate] = None
  ): TaskItem =
    TaskItem
      .create("Gorev", Some("aciklama"), priority, dueDate, userId = 1L, now, by)
      .fold(e => fail(s"Beklenmeyen hata: $e"), identity)

  "TaskItem.create" should {

    "High oncelikte dueDate yoksa HighPriorityRequiresDueDate dondurmeli" in {
      TaskItem.create("x", None, Priority.High, None, 1L, now, by) shouldBe
        Left(DomainError.HighPriorityRequiresDueDate)
    }

    "bos baslikta EmptyTitle dondurmeli" in {
      TaskItem.create("   ", None, Priority.Low, None, 1L, now, by) shouldBe
        Left(DomainError.EmptyTitle)
    }

    "userId 0 ise InvalidUserId dondurmeli" in {
      TaskItem.create("x", None, Priority.Low, None, 0L, now, by) shouldBe
        Left(DomainError.InvalidUserId)
    }

    "gecerli girdide id=0, isCompleted=false, completedAt=None ile uretmeli" in {
      val task = validTask()
      task.id shouldBe 0L
      task.isCompleted shouldBe false
      task.completedAt shouldBe None
      task.userId shouldBe Some(1L)
    }
  }

  "TaskItem.complete" should {

    "son tarihi gecmis gorevde TaskPastDueCannotComplete dondurmeli" in {
      val task = validTask(dueDate = Some(today.minusDays(1)))
      task.complete(today, now) shouldBe Left(DomainError.TaskPastDueCannotComplete)
    }

    "son tarihi bugun olan gorevi tamamlayabilmeli" in {
      val task = validTask(dueDate = Some(today))
      task.complete(today, now).map(_.isCompleted) shouldBe Right(true)
    }

    "gelecekteki son tarihli gorevi tamamlamali ve completedAt yazmali" in {
      val task = validTask(dueDate = Some(today.plusDays(3)))
      val completed = task.complete(today, now)
      completed.map(_.isCompleted) shouldBe Right(true)
      completed.map(_.completedAt) shouldBe Right(Some(now))
    }

    "idempotent olmali: ikinci cagri completedAt'i degistirmemeli" in {
      val first = validTask().complete(today, now).getOrElse(fail())
      val later = Instant.parse("2026-06-09T10:00:00Z")
      val second = first.complete(today, later).getOrElse(fail())
      second.completedAt shouldBe Some(now)
    }
  }

  "TaskItem.reopen" should {

    "tamamlanmis gorevi acmali ve completedAt'i temizlemeli" in {
      val completed = validTask().complete(today, now).getOrElse(fail())
      val reopened = completed.reopen()
      reopened.isCompleted shouldBe false
      reopened.completedAt shouldBe None
    }

    "zaten acik gorevde idempotent olmali (ayni nesne)" in {
      val task = validTask()
      task.reopen() should be theSameInstanceAs task
    }
  }

  "TaskItem.edit oncelik/son tarih capraz kurali" should {

    "High oncelige gecerken dueDate verilmezse HighPriorityRequiresDueDate dondurmeli" in {
      validTask().edit("Gorev", None, Priority.High, None) shouldBe
        Left(DomainError.HighPriorityRequiresDueDate)
    }

    "High'a gecisle dueDate ayni anda verildiginde basarili olmali (nihai durum dogrulamasi)" in {
      val due = Some(today.plusDays(1))
      val edited = validTask(priority = Priority.Low).edit("Gorev", None, Priority.High, due)
      edited.map(t => (t.priority, t.dueDate)) shouldBe Right((Priority.High, due))
    }

    "High gorevde dueDate temizlenmek istenirse hata dondurmeli" in {
      val highTask = validTask(priority = Priority.High, dueDate = Some(today.plusDays(1)))
      highTask.edit("Gorev", None, Priority.High, None) shouldBe
        Left(DomainError.HighPriorityRequiresDueDate)
    }
  }

  "TaskItem.assignToCategory" should {

    val category = Category
      .create("Is", "aciklama", 1L, now, by)
      .map(_.copy(id = 5L))
      .getOrElse(fail())

    "silinmis kategoriye atamada CategoryDeleted dondurmeli" in {
      val deleted = category.markDeleted(now, by)
      validTask().copy(id = 1L).assignToCategory(deleted, Seq.empty, now, by) shouldBe
        Left(DomainError.CategoryDeleted)
    }

    "aktif iliski zaten varsa idempotent olmali (Right(None))" in {
      val task = validTask().copy(id = 1L)
      val existing = TaskItemCategory
        .create(task.id, category.id, now, by)
        .map(_.copy(id = 9L))
        .getOrElse(fail())
      task.assignToCategory(category, Seq(existing), now, by) shouldBe Right(None)
    }

    "iliski yoksa yeni bir link dondurmeli" in {
      val task = validTask().copy(id = 1L)
      val result = task.assignToCategory(category, Seq.empty, now, by)
      result.map(_.map(l => (l.taskItemId, l.categoryId))) shouldBe Right(Some((1L, 5L)))
    }
  }

  "TaskItem.softDeleteWithUser" should {

    "userId'yi None yapmali ve isDeleted'i true yapmali" in {
      val deleted = validTask().softDeleteWithUser(by, now)
      deleted.userId shouldBe None
      deleted.isDeleted shouldBe true
    }
  }
}
