package services

import java.time.{Instant, LocalDate}

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import domain.common.DomainError
import repositories.inmemory.InMemoryUserRepository
import support.{FixedClock, TestDatabase}

/** UserService: register (benzersizlik) + login (kimlik dogrulama). */
class UserServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val today = LocalDate.of(2026, 6, 8)

  private def newService() =
    new UserServiceImpl(new InMemoryUserRepository(TestDatabase.empty()), FixedClock(now, today))

  "UserService.register" should {
    "yeni kullaniciyi olusturmali ve audit createdBy='system' olmali" in {
      val service = newService()
      val created = service.register("a@b.com", "sifre").futureValue
      created.map(_.email) shouldBe Right("a@b.com")
      created.map(_.audit.createdBy) shouldBe Right("system")
    }
    "ayni email ikinci kez kayit edilemez (EmailAlreadyTaken)" in {
      val service = newService()
      service.register("a@b.com", "sifre").futureValue
      service.register("a@b.com", "baska").futureValue shouldBe Left(DomainError.EmailAlreadyTaken)
    }
    "bos email/parolayi domain reddeder" in {
      val service = newService()
      service.register("  ", "sifre").futureValue shouldBe Left(DomainError.EmptyEmail)
    }
  }

  "UserService.login" should {
    "dogru email+parola ile kullaniciyi dondurmeli" in {
      val service = newService()
      val u = service.register("a@b.com", "sifre").futureValue.getOrElse(fail())
      service.login("a@b.com", "sifre").futureValue.map(_.id) shouldBe Right(u.id)
    }
    "yanlis parolada InvalidCredentials dondurmeli" in {
      val service = newService()
      service.register("a@b.com", "sifre").futureValue
      service.login("a@b.com", "yanlis").futureValue shouldBe Left(DomainError.InvalidCredentials)
    }
    "olmayan email'de InvalidCredentials dondurmeli" in {
      val service = newService()
      service.login("yok@b.com", "sifre").futureValue shouldBe Left(DomainError.InvalidCredentials)
    }
  }
}
