package repositories

import java.time.{Instant, LocalDate}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import domain.common.Priority
import domain.task.TaskItem
import support.TestDatabase

/** Bellek-ici TaskItem repository: id atama ve soft-delete filtreleme (R9). */
class InMemoryTaskItemRepositorySpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val today = LocalDate.of(2026, 6, 8)

  private def newTask(title: String): TaskItem =
    TaskItem.create(title, None, Priority.Low, None, 1L, now, "system").getOrElse(fail())

  "InMemoryTaskItemRepository" should {

    "add ile artan id atamali" in {
      val repo = new InMemoryTaskItemRepository(TestDatabase.empty())
      repo.add(newTask("a")).id shouldBe 1L
      repo.add(newTask("b")).id shouldBe 2L
    }

    "list() soft-delete edilmis kayitlari elemeli, ama gercekte saklamali" in {
      val db = TestDatabase.empty()
      val repo = new InMemoryTaskItemRepository(db)

      val saved = repo.add(newTask("silinecek"))
      repo.list().map(_.id) should contain(saved.id)

      // Soft-delete: userId kopar + audit.deleted, sonra persist
      repo.update(saved.softDeleteWithUser("system", now))

      repo.list().map(_.id) should not contain saved.id
      db.tasks.findById(saved.id, includeDeleted = true).map(_.isDeleted) shouldBe Some(true)
    }
  }
}
