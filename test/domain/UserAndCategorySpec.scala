package domain

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import domain.category.Category
import domain.common.DomainError
import domain.user.User

/** User ve Category temel invariant testleri. */
class UserAndCategorySpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-08T10:00:00Z")
  private val by = "tester"

  "User.create" should {
    "bos email'i reddetmeli" in {
      User.create("  ", "sifre", now, by) shouldBe Left(DomainError.EmptyEmail)
    }
    "bos parolayi reddetmeli" in {
      User.create("a@b.com", "  ", now, by) shouldBe Left(DomainError.EmptyPassword)
    }
    "email'i trim'lemeli" in {
      User.create("  a@b.com  ", "sifre", now, by).map(_.email) shouldBe Right("a@b.com")
    }
  }

  "User.changeEmail" should {
    "bos email'i reddetmeli" in {
      val user = User.create("a@b.com", "sifre", now, by).getOrElse(fail())
      user.changeEmail("") shouldBe Left(DomainError.EmptyEmail)
    }
  }

  "Category.create" should {
    "bos adi reddetmeli" in {
      Category.create("", "aciklama", 1L, now, by) shouldBe Left(DomainError.EmptyCategoryName)
    }
    "bos aciklamayi reddetmeli (Category'de aciklama zorunlu)" in {
      Category.create("Ad", "  ", 1L, now, by) shouldBe Left(DomainError.EmptyCategoryDescription)
    }
    "userId 0 ise reddetmeli" in {
      Category.create("Ad", "aciklama", 0L, now, by) shouldBe Left(DomainError.InvalidUserId)
    }
  }
}
