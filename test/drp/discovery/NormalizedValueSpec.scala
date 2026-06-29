package drp.discovery

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import drp.discovery.domain.NormalizedValue

class NormalizedValueSpec extends AnyWordSpec with Matchers {

  "NormalizedValue.from" should {

    "extract hostname from a full URL, discarding scheme/path/query/fragment" in {
      NormalizedValue.from("https://akbank-guvenli-giris.com/login?ref=1").value shouldBe "akbank-guvenli-giris.com"
    }

    "lower-case and remove trailing dot from an FQDN" in {
      NormalizedValue.from("AKBANK-GUVENLI.COM.").value shouldBe "akbank-guvenli.com"
    }

    "preserve the www label" in {
      NormalizedValue.from("https://www.akbank-fake.com/path?q=1").value shouldBe "www.akbank-fake.com"
    }

    "leave already-Punycode IDN unchanged" in {
      NormalizedValue.from("xn--nxasmq6b.com").value shouldBe "xn--nxasmq6b.com"
    }

    "discard credentials, port, path, and query" in {
      NormalizedValue.from("http://user:pass@akbank-fake.com:8080/x").value shouldBe "akbank-fake.com"
    }

    "handle bare hostname without scheme" in {
      NormalizedValue.from("akbank-fake.com").value shouldBe "akbank-fake.com"
    }

    "handle bare hostname with trailing dot" in {
      NormalizedValue.from("akbank-fake.com.").value shouldBe "akbank-fake.com"
    }

    "produce invalid: prefix for non-empty malformed input" in {
      val result = NormalizedValue.from("not a domain !! @@")
      result.value should startWith("invalid:")
    }

    "collapse multiple whitespace chars in malformed fallback" in {
      val result = NormalizedValue.from("bad  input")
      result.value shouldBe "invalid:bad input"
    }

    "be idempotent — applying from() twice on a valid domain returns the same value" in {
      val first  = NormalizedValue.from("AKBANK-FAKE.COM.").value
      val second = NormalizedValue.from(first).value
      first shouldBe second
    }

    "be idempotent — applying from() twice on a malformed value returns the same value" in {
      val first  = NormalizedValue.from("bad input @@").value
      val second = NormalizedValue.from(first).value
      // second pass on "invalid:bad input @@" — still invalid:
      second should startWith("invalid:")
    }
  }
}
