package drp.asset.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExclusionSpec extends AnyWordSpec with Matchers {

  "Exclusion.create" should {

    "build a valid exclusion with the entered value, parsed match type, and DB-default fields" in {
      val result = Exclusion.create(1L, "akbankdirekt.com", "exact", "owned_unmonitored")
      result.map(_.value) shouldBe Right("akbankdirekt.com")
      result.map(_.matchType) shouldBe Right(ExclusionMatchType.Exact)
      result.map(_.reason) shouldBe Right("owned_unmonitored")
      result.map(_.isActive) shouldBe Right(true)
      result.map(_.createdBy) shouldBe Right("system")
    }

    "reject a blank value" in {
      Exclusion.create(1L, "   ", "exact", "owned_unmonitored") shouldBe Left(AssetDomainError.BlankExclusionValue)
    }

    "reject a blank reason" in {
      Exclusion.create(1L, "akbankdirekt.com", "exact", "  ") shouldBe Left(AssetDomainError.BlankExclusionReason)
    }

    "reject a match type outside the allowed set" in {
      Exclusion.create(1L, "akbankdirekt.com", "wildcard", "owned_unmonitored") shouldBe
        Left(AssetDomainError.InvalidMatchType("wildcard"))
    }

    "accept each of the four allowed match types" in {
      Seq("exact", "registrable_domain", "subdomain_of", "pattern").foreach { mt =>
        Exclusion.create(1L, "x.com", mt, "manual").map(_.matchType.asValue) shouldBe Right(mt)
      }
    }
  }
}
