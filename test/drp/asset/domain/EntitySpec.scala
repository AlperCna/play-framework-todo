package drp.asset.domain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EntitySpec extends AnyWordSpec with Matchers {

  "Entity.create" should {

    "reject a blank name" in {
      Entity.create("   ", "brand") shouldBe Left(AssetDomainError.BlankEntityName)
    }

    "accept any non-blank type (free text)" in {
      Entity.create("Akbank", "institution").map(_.entityType) shouldBe Right("institution")
    }

    "trim the name" in {
      Entity.create("  Akbank  ", "brand").map(_.name) shouldBe Right("Akbank")
    }
  }
}
