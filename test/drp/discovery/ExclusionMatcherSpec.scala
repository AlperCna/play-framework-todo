package drp.discovery

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.asset.application.ports.ExclusionView
import drp.discovery.application.ExclusionMatcher
import drp.discovery.domain.{NormalizedValue, SkipReason}

class ExclusionMatcherSpec extends AnyWordSpec with Matchers {

  private def excl(value: String, matchType: String) = ExclusionView(value, matchType, "legal")

  private def nv(raw: String) = NormalizedValue.from(raw)

  "ExclusionMatcher.matches — exact" should {
    val exclusions = Seq(excl("akbank.com", "exact"))

    "match when normalized discovery equals exclusion value" in {
      ExclusionMatcher.matches(nv("akbank.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "not match a subdomain against an exact exclusion" in {
      ExclusionMatcher.matches(nv("login.akbank.com"), exclusions) shouldBe None
    }
    "not match a different domain" in {
      ExclusionMatcher.matches(nv("akbank-fake.com"), exclusions) shouldBe None
    }
  }

  "ExclusionMatcher.matches — registrable_domain" should {
    val exclusions = Seq(excl("akbank.com", "registrable_domain"))

    "match when both share the same registrable domain" in {
      ExclusionMatcher.matches(nv("www.akbank.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "match the exact base domain itself" in {
      ExclusionMatcher.matches(nv("akbank.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "not match a different registrable domain" in {
      ExclusionMatcher.matches(nv("akbank-fake.com"), exclusions) shouldBe None
    }
  }

  "ExclusionMatcher.matches — subdomain_of" should {
    val exclusions = Seq(excl("akbank.com", "subdomain_of"))

    "match a subdomain of the exclusion host" in {
      ExclusionMatcher.matches(nv("login.akbank.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "match a deeper subdomain" in {
      ExclusionMatcher.matches(nv("secure.login.akbank.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "NOT match the exclusion host itself" in {
      ExclusionMatcher.matches(nv("akbank.com"), exclusions) shouldBe None
    }
    "not match a different domain ending with a similar suffix" in {
      ExclusionMatcher.matches(nv("fakeakbank.com"), exclusions) shouldBe None
    }
  }

  "ExclusionMatcher.matches — pattern" should {
    val exclusions = Seq(excl("*akbank*", "pattern"))

    "match a domain containing the pattern keyword" in {
      ExclusionMatcher.matches(nv("secure-akbank-login.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "match when the pattern covers the full domain" in {
      ExclusionMatcher.matches(nv("akbank.com"), exclusions) shouldBe Some(SkipReason.Whitelisted)
    }
    "not match a domain that does not contain the keyword" in {
      ExclusionMatcher.matches(nv("garanti-bbva.com"), exclusions) shouldBe None
    }
  }

  "ExclusionMatcher.matches — first match wins" should {
    "return whitelisted on the first matching exclusion and not continue" in {
      val multi = Seq(
        excl("akbank.com", "exact"),
        excl("*fake*", "pattern")
      )
      ExclusionMatcher.matches(nv("akbank-fake.com"), multi) shouldBe Some(SkipReason.Whitelisted)
    }
  }

  "ExclusionMatcher.matches — no match" should {
    "return None when no exclusion applies" in {
      val exclusions = Seq(excl("akbank.com", "exact"), excl("garanti.com", "exact"))
      ExclusionMatcher.matches(nv("akbank-guvenli-giris.com"), exclusions) shouldBe None
    }
    "return None for an empty exclusion list" in {
      ExclusionMatcher.matches(nv("akbank.com"), Seq.empty) shouldBe None
    }
  }
}
