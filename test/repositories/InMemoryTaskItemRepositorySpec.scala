package repositories

import java.time.Instant

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import todo.shared.domain.Priority
import todo.task.domain.TaskItem
import todo.task.infrastructure.InMemoryTaskItemRepository
import support.TestDatabase

class InMemoryTaskItemRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val now = Instant.parse("2026-06-08T10:00:00Z")

  private def newTask(title: String): TaskItem =
    TaskItem.create(title, None, Priority.Low, None, 1L, now, "system").getOrElse(fail())

  "InMemoryTaskItemRepository" should {

    "add ile artan id atamali" in {
      val repo = new InMemoryTaskItemRepository(TestDatabase.empty())
      repo.add(newTask("a")).futureValue.id shouldBe 1L
      repo.add(newTask("b")).futureValue.id shouldBe 2L
    }

    "list() soft-delete edilmis kayitlari elemeli, ama gercekte saklamali" in {
      val db = TestDatabase.empty()
      val repo = new InMemoryTaskItemRepository(db)

      val saved = repo.add(newTask("silinecek")).futureValue
      repo.list().futureValue.map(_.id) should contain(saved.id)

      repo.update(saved.softDeleteWithUser("system", now)).futureValue

      repo.list().futureValue.map(_.id) should not contain saved.id
      db.tasks.findById(saved.id, includeDeleted = true).map(_.isDeleted) shouldBe Some(true)
    }
  }
}
