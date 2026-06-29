package drp.asset.domain

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.shared.domain.DomainError

class AssetSpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-29T00:00:00Z")
  private val e1 = EntityId(1L)

  "Asset.create" should {
    "succeed with a non-blank value and a valid type, defaulting active + ungrouped" in {
      val a = Asset.create(e1, "domain", "akbank.com", AssetMetadata.empty, now).toOption.get
      a.value shouldBe "akbank.com"
      a.assetType shouldBe AssetType.Domain
      a.isActive shouldBe true
      a.assetGroupId shouldBe None
    }
    "reject a blank value" in {
      Asset.create(e1, "domain", "   ", AssetMetadata.empty, now) shouldBe Left(DomainError.EmptyAssetValue)
    }
    "reject an unknown asset type (closed enum)" in {
      Asset.create(e1, "ftp", "x.com", AssetMetadata.empty, now) shouldBe Left(DomainError.InvalidAssetType("ftp"))
    }
  }

  "AssetMetadata.of" should {
    "turn blank refs into None" in {
      AssetMetadata.of("https://akbank.com", "  ", "", "fav") shouldBe
        AssetMetadata(Some("https://akbank.com"), None, None, Some("fav"))
    }
  }
}
