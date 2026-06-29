package drp.asset.domain

import java.time.Instant

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.shared.domain.DomainError

class AssetGroupSpec extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-06-29T00:00:00Z")
  private val e1 = EntityId(1L)

  "AssetGroup.create" should {
    "succeed with a non-blank name" in {
      AssetGroup.create(e1, "Akbank Direkt", now) shouldBe
        Right(AssetGroup(AssetGroupId(0L), e1, "Akbank Direkt", now, now))
    }
    "reject a blank name" in {
      AssetGroup.create(e1, "  ", now) shouldBe Left(DomainError.EmptyAssetGroupName)
    }
  }
}
