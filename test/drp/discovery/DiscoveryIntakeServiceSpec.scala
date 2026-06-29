package drp.discovery

import java.time.Instant

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.time.{Millis, Seconds, Span}

import drp.asset.application.ports.{AssetReadPort, AssetView, EntityView, EntityWithAssets, ExclusionView}
import drp.asset.domain.EntityId
import drp.discovery.application.{DiscoveryIntakeServiceImpl, DiscoveryStatusFilter}
import drp.discovery.application.ports.PermutationProvider
import drp.discovery.domain.{DiscoveryId, DiscoverySource, DnsStatus, NormalizedValue, SkipReason}
import drp.discovery.infrastructure.{FakePermutationProvider}
import drp.discovery.infrastructure.inmemory.InMemoryDiscoveryRepository
import drp.shared.application.{Page, PageRequest}
import drp.shared.domain.DomainError

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class DiscoveryIntakeServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit val pc: PatienceConfig = PatienceConfig(Span(5, Seconds), Span(50, Millis))

  private val entity   = EntityView(1L, "Akbank", "brand")
  private val domainAsset = AssetView(10L, 1L, "domain", "akbank.com", isActive = true)
  private val exclusions  = Seq(ExclusionView("akbank.com", "exact", "legal"))

  private def makeReadPort(
      assets: Seq[AssetView] = Seq(domainAsset),
      excls: Seq[ExclusionView] = exclusions
  ): AssetReadPort = new AssetReadPort {
    override def activeExclusions(entityId: EntityId): Future[Seq[ExclusionView]] =
      Future.successful(excls)
    override def resolveEntityWithAssets(entityId: EntityId): Future[Option[EntityWithAssets]] =
      if (entityId.value == 1L) Future.successful(Some(EntityWithAssets(entity, assets)))
      else Future.successful(None)
    override def listEntities(page: drp.shared.application.PageRequest): Future[drp.shared.application.Page[EntityView]] =
      Future.successful(drp.shared.application.Page(Seq(entity), 1, 200, 1L))
    override def resolveAsset(assetId: Long): Future[Option[AssetView]] =
      Future.successful(assets.find(_.id == assetId))
  }

  private def makeService(
      readPort: AssetReadPort = makeReadPort(),
      provider: PermutationProvider = new FakePermutationProvider(Seq.empty)
  ) = {
    val repo = new InMemoryDiscoveryRepository()
    val svc  = new DiscoveryIntakeServiceImpl(readPort, repo, provider)
    (svc, repo)
  }

  "submitManual" should {

    "save a valid domain with dns_status=pending and no skip_reason" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      result shouldBe a[Right[_, _]]
      val d = result.toOption.get
      d.dnsStatus shouldBe DnsStatus.Pending
      d.skipReason shouldBe None
      d.normalizedValue.value shouldBe "akbank-guvenli-giris.com"
    }

    "normalize the submitted URL to its hostname" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, None, "https://akbank-guvenli-giris.com/login?ref=1").value.futureValue
      result.toOption.get.normalizedValue.value shouldBe "akbank-guvenli-giris.com"
    }

    "set skip_reason=whitelisted when domain matches an active exclusion" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, None, "akbank.com").value.futureValue
      result.toOption.get.skipReason shouldBe Some(SkipReason.Whitelisted)
    }

    "whitelist even when exclusion value is stored in non-canonical form (uppercase / trailing dot)" in {
      val upperExclusion = ExclusionView("AKBANK.COM.", "exact", "legal")
      val (svc, _) = makeService(readPort = makeReadPort(excls = Seq(upperExclusion)))
      val result = svc.submitManual(1L, None, "akbank.com").value.futureValue
      result.toOption.get.skipReason shouldBe Some(SkipReason.Whitelisted)
    }

    "set skip_reason=invalid_format for malformed input" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, None, "not a domain !! @@").value.futureValue
      result.toOption.get.skipReason shouldBe Some(SkipReason.InvalidFormat)
    }

    "return the existing record without creating a duplicate (same normalized value)" in {
      val (svc, repo) = makeService()
      svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      svc.submitManual(1L, None, "AKBANK-GUVENLI-GIRIS.COM.").value.futureValue
      repo.listByEntity(1L, None, PageRequest.of(1)).futureValue.total shouldBe 1L
    }

    "return EmptyDiscoveryValue for a blank raw value" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, None, "   ").value.futureValue
      result shouldBe Left(DomainError.EmptyDiscoveryValue)
    }

    "return EntityNotFound when the entity does not exist" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(99L, None, "akbank-fake.com").value.futureValue
      result shouldBe Left(DomainError.EntityNotFound(99L))
    }

    "return AssetEntityMismatch when an asset from a different entity is provided" in {
      val foreignAsset = AssetView(99L, 2L, "domain", "garanti.com", isActive = true)
      // entity 1 only has domainAsset (id=10), not 99
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, Some(99L), "garanti-fake.com").value.futureValue
      result shouldBe Left(DomainError.AssetEntityMismatch(99L, 1L))
    }

    "set asset_id to None when assetId is not provided" in {
      val (svc, _) = makeService()
      val result = svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      result.toOption.get.assetId shouldBe None
    }
  }

  "requestPermutation" should {

    "stage new unique results from the provider with source=permutation" in {
      val provider = new FakePermutationProvider(Seq("akbank-guvenli-odeme.com", "akbank-login.com"))
      val (svc, repo) = makeService(provider = provider)
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result shouldBe Right(2)
      repo.listByEntity(1L, None, PageRequest.of(1)).futureValue.items.forall(_.source == DiscoverySource.Permutation) shouldBe true
    }

    "skip already-staged values (deduplication)" in {
      val provider = new FakePermutationProvider(Seq("akbank-guvenli-odeme.com"))
      val (svc, repo) = makeService(provider = provider)
      svc.requestPermutation(1L, 10L).value.futureValue
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result shouldBe Right(0)
      repo.listByEntity(1L, None, PageRequest.of(1)).futureValue.total shouldBe 1L
    }

    "filter blank values from provider results" in {
      val provider = new FakePermutationProvider(Seq("", "   ", "akbank-guvenli-odeme.com"))
      val (svc, repo) = makeService(provider = provider)
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result shouldBe Right(1)
    }

    "return AssetNotDomainType for a non-domain asset" in {
      val subAsset = AssetView(10L, 1L, "subdomain", "login.akbank.com", isActive = true)
      val (svc, _) = makeService(readPort = makeReadPort(assets = Seq(subAsset)))
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result shouldBe Left(DomainError.AssetNotDomainType(10L))
    }

    "return AssetNotActive for an inactive domain asset" in {
      val inactiveAsset = AssetView(10L, 1L, "domain", "akbank.com", isActive = false)
      val (svc, _) = makeService(readPort = makeReadPort(assets = Seq(inactiveAsset)))
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result shouldBe Left(DomainError.AssetNotActive(10L))
    }

    "return AssetNotFound for unknown assetId" in {
      val (svc, _) = makeService()
      val result = svc.requestPermutation(1L, 999L).value.futureValue
      result shouldBe Left(DomainError.AssetNotFound(999L))
    }

    "deduplicate values that normalize to the same hostname within a single batch" in {
      // Provider returns the same domain in two different casings/formats; only one record must be saved.
      val provider = new FakePermutationProvider(Seq("akbank-login.com", "AKBANK-LOGIN.COM.", "https://akbank-login.com/"))
      val (svc, repo) = makeService(provider = provider)
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result shouldBe Right(1)
      repo.listByEntity(1L, None, PageRequest.of(1)).futureValue.total shouldBe 1L
    }

    "return PermutationProviderFailure and write no records on provider failure" in {
      val failingProvider = new PermutationProvider {
        override def generateLookAlikes(assetValue: String): Future[Seq[String]] =
          Future.failed(new RuntimeException("network error"))
      }
      val (svc, repo) = makeService(provider = failingProvider)
      val result = svc.requestPermutation(1L, 10L).value.futureValue
      result.left.toOption.get shouldBe a[DomainError.PermutationProviderFailure]
      repo.listByEntity(1L, None, PageRequest.of(1)).futureValue.total shouldBe 0L
    }
  }

  "listDiscoveries" should {

    "return all records when no filter applied" in {
      val (svc, _) = makeService()
      svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      svc.submitManual(1L, None, "akbank.com").value.futureValue            // whitelisted
      svc.submitManual(1L, None, "not a domain !!").value.futureValue       // invalid_format
      val page = svc.listDiscoveries(1L, None, PageRequest.of(1)).futureValue
      page.total shouldBe 3L
    }

    "return only pending records when PendingValidation filter applied" in {
      val (svc, _) = makeService()
      svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      svc.submitManual(1L, None, "akbank.com").value.futureValue
      val page = svc.listDiscoveries(1L, Some(DiscoveryStatusFilter.PendingValidation), PageRequest.of(1)).futureValue
      page.items.forall(d => d.skipReason.isEmpty && d.dnsStatus == DnsStatus.Pending) shouldBe true
    }

    "return only whitelisted records when Whitelisted filter applied" in {
      val (svc, _) = makeService()
      svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      svc.submitManual(1L, None, "akbank.com").value.futureValue
      val page = svc.listDiscoveries(1L, Some(DiscoveryStatusFilter.Whitelisted), PageRequest.of(1)).futureValue
      page.items.forall(_.skipReason.contains(SkipReason.Whitelisted)) shouldBe true
    }

    "return only invalid_format records when InvalidFormat filter applied" in {
      val (svc, _) = makeService()
      svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue
      svc.submitManual(1L, None, "not a domain !!").value.futureValue
      val page = svc.listDiscoveries(1L, Some(DiscoveryStatusFilter.InvalidFormat), PageRequest.of(1)).futureValue
      page.items.forall(_.skipReason.contains(SkipReason.InvalidFormat)) shouldBe true
    }
  }

  "getDiscovery" should {

    "return the discovery when it exists" in {
      val (svc, _) = makeService()
      val saved = svc.submitManual(1L, None, "akbank-guvenli-giris.com").value.futureValue.toOption.get
      val result = svc.getDiscovery(DiscoveryId(saved.id.value)).value.futureValue
      result.toOption.get.normalizedValue.value shouldBe "akbank-guvenli-giris.com"
    }

    "return DiscoveryNotFound for an unknown id" in {
      val (svc, _) = makeService()
      val result = svc.getDiscovery(DiscoveryId(999L)).value.futureValue
      result shouldBe Left(DomainError.DiscoveryNotFound(999L))
    }
  }
}
