package drp.asset.domain

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.shared.domain.DomainError

class ExclusionSpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-29T00:00:00Z")
  private val e1 = EntityId(1L)

  "Exclusion.create" should {
    "store the value verbatim with createdBy=system, active, known reason" in {
      val x = Exclusion.create(e1, "akbankdirekt.com", "exact", "owned_unmonitored", now).toOption.get
      x.value shouldBe "akbankdirekt.com"
      x.matchType shouldBe MatchType.Exact
      x.reason shouldBe ExclusionReason.OwnedUnmonitored
      x.isActive shouldBe true
      x.createdBy shouldBe "system"
    }
    "tolerate an unknown reason as Other (open enum)" in {
      Exclusion.create(e1, "x.com", "exact", "legal_hold", now).map(_.reason) shouldBe
        Right(ExclusionReason.Other("legal_hold"))
    }
    "reject a blank value" in {
      Exclusion.create(e1, "  ", "exact", "manual", now) shouldBe Left(DomainError.EmptyExclusionValue)
    }
    "reject an unknown match type (closed enum)" in {
      Exclusion.create(e1, "x.com", "fuzzy", "manual", now) shouldBe Left(DomainError.InvalidMatchType("fuzzy"))
    }
  }
}
