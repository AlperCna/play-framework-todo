# Mona DRP — Domain Model

Bu döküman, Mona DRP platformunun domain katmanını açıklar. Tüm domain objeleri
`app/drp/` altında modüler monolit yapısına göre organize edilmiştir.

---

## İçindekiler

1. [Tasarım Prensipleri](#1-tasarım-prensipleri)
2. [Shared Domain](#2-shared-domain)
3. [Asset Modülü](#3-asset-modülü)
4. [Discovery Modülü](#4-discovery-modülü)
5. [Candidate Modülü](#5-candidate-modülü)
6. [Crawl Modülü](#6-crawl-modülü)
7. [Analysis Modülü](#7-analysis-modülü)
8. [Risk Modülü](#8-risk-modülü)
9. [Review Modülü](#9-review-modülü)
10. [Casework Modülü](#10-casework-modülü)
11. [Platform / Storage Modülü](#11-platform--storage-modülü)
12. [Pipeline Akışı](#12-pipeline-akışı)

---

## 1. Tasarım Prensipleri

### Sealed ADT vs Open Value Object

Domain'de iki tür enum kullanılır:

**Sealed ADT** — Veritabanında `CHECK constraint` olan, kapalı küme değerler için.
Yeni bir değer eklemek şema değişikliği gerektirir; derleyici tüm case'lerin ele
alındığını garanti eder.

```scala
sealed trait CandidateStatus { def value: String }
object CandidateStatus {
  case object Validated extends CandidateStatus { val value = "validated" }
  case object Crawled   extends CandidateStatus { val value = "crawled" }
  // ...
}
```

**Open Value Object** — Veritabanında CHECK constraint olmayan, zamanla
genişleyebilecek değerler için. `String` wrapper olarak tanımlanır.

```scala
final case class DiscoverySource(value: String) extends AnyVal
object DiscoverySource {
  def create(value: String): Either[DomainError, DiscoverySource] =
    CommonValues.nonEmpty("discoverySource", value).map(DiscoverySource(_))
}
```

### Smart Constructor

Her entity doğrudan `new` ile oluşturulmaz. `create()` factory methodu
validasyonu `Either[DomainError, T]` ile döndürür.

```scala
object Asset {
  def create(...): Either[DomainError, Asset] =
    CommonValues.nonEmpty("value", value).map(Asset(...))
}
```

### Typed ID'ler

Her entity `Long` yerine kendi wrapper ID tipini kullanır. Bu sayede
`assetId` ile `candidateId` yanlışlıkla karıştırılamaz; derleyici hata verir.

```scala
final case class AssetId(value: Long)     extends AnyVal
final case class CandidateId(value: Long) extends AnyVal
// Bunlar birbirine atanamaz — compile-time hata
```

### Immutability

Tüm domain objeleri `case class` olarak tanımlanmıştır; `var` kullanılmaz.
Durum değişikliği her zaman yeni bir kopya döndürür.

```scala
def deactivate(updatedAt: Instant): Asset =
  copy(isActive = false, updatedAt = updatedAt)
```

---

## 2. Shared Domain

**Paket:** `drp.shared.domain`

Tüm modüllerin ortak kullandığı temel tipler bu pakette tutulur.

### 2.1 ID Tipleri — `Ids.scala`

Platformdaki her tablo için ayrı bir ID tipi tanımlanmıştır. Hepsi
`AnyVal` wrapper olduğu için runtime'da ekstra nesne oluşturulmaz.

| Tip | Karşılık Geldiği Tablo |
|---|---|
| `EntityId` | `entities` |
| `AssetGroupId` | `asset_groups` |
| `AssetId` | `assets` |
| `ExclusionId` | `exclusions` |
| `DiscoveryId` | `candidate_discoveries` |
| `CandidateId` | `candidates` |
| `BlobStorageId` | `blob_storage` |
| `CrawlResultId` | `crawl_results` |
| `PageFeatureId` | `page_features` |
| `CandidateAssetMatchId` | `candidate_asset_matches` |
| `DetectionSignalId` | `detection_signals` |
| `RiskScoreId` | `risk_scores` |
| `RuleResultId` | `rule_results` |
| `ReviewId` | `reviews` |
| `CaseId` | `cases` |
| `EvidenceFileId` | `evidence_files` |

### 2.2 DomainError — `DomainError.scala`

Tüm domain validasyon hataları bu sealed trait altında toplanır.
`Either[DomainError, T]` döndüren her smart constructor bu tipleri kullanır.

| Case | Açıklama |
|---|---|
| `EmptyField(field)` | Zorunlu bir alan boş bırakıldı |
| `InvalidRange(field, min, max)` | Sayısal değer geçerli aralık dışında |
| `InvalidHttpStatus(value)` | HTTP status 100–599 aralığı dışında |
| `InvalidStatusTransition(from, to)` | Geçersiz durum geçişi (örn. Crawled → Reviewed) |
| `NegativeValue(field, value)` | Negatif olamayacak bir alan negatif |
| `NotFound(entity, id)` | İstenen kayıt bulunamadı |

### 2.3 Ortak Değer Tipleri — `CommonValues.scala`

| Tip | Açıklama |
|---|---|
| `AuditInfo(createdAt, updatedAt)` | Mutable entity'lerde audit zaman damgaları |
| `StorageRef` | Blob storage referans URL'i (`pg://evidence/...`) |
| `ContentHash` | Dosya içerik hash'i (duplikasyon tespiti için) |
| `Score` | 0.0–1.0 aralığında ondalıklı skor (base tip) |
| `Metadata` | `Map[String, MetadataValue]` — JSONB sütunu için tip-güvenli wrapper |
| `MetadataValue` | `Text \| Number \| Bool \| Obj \| Arr` — JSONB değer ADT'si |

**StorageRef formatı:**
```
pg://evidence/{candidate_id}/{crawl_run_id}/{file_type}.{ext}   ← MVP
s3://mona-drp/evidence/{candidate_id}/{crawl_run_id}/...        ← İleride
```

---

## 3. Asset Modülü

**Paket:** `drp.asset.domain`
**Tablolar:** `entities`, `asset_groups`, `assets`, `exclusions`

Korunan markaları, dijital varlıklarını ve allowlist (whitelist) kayıtlarını yönetir.

### 3.1 Entity — `Entity.scala`

Korunan bir markayı, kurumu veya kişiyi temsil eder.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `EntityId` | Birincil anahtar |
| `name` | `String` | Marka adı (örn. "Akbank") |
| `entityType` | `EntityType` | Kurum tipi — open value object |
| `createdAt` | `Instant` | Oluşturulma zamanı |
| `updatedAt` | `Instant` | Son güncelleme zamanı |

**`EntityType`:** Veritabanında CHECK constraint olmadığı için open value object.
Zaman içinde `"bank"`, `"telecom"`, `"ecommerce"` gibi yeni tipler eklenebilir.

**Validasyon:** `name` boş olamaz.

---

### 3.2 AssetGroup — `AssetGroup.scala`

Bir entity altında opsiyonel alt gruplama sağlar.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `AssetGroupId` | Birincil anahtar |
| `entityId` | `EntityId` | Ait olduğu entity |
| `name` | `String` | Grup adı |
| `createdAt` | `Instant` | — |
| `updatedAt` | `Instant` | — |

**Validasyon:** `name` boş olamaz.

---

### 3.3 Asset — `Asset.scala`

Korunan dijital varlık. Genellikle bir domain veya subdomain.
Logo ve favicon ayrı tip değildir; referansları `metadata` JSONB alanında saklanır.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `AssetId` | Birincil anahtar |
| `entityId` | `EntityId` | Ait olduğu entity |
| `assetGroupId` | `Option[AssetGroupId]` | Opsiyonel grup |
| `assetType` | `AssetType` | `Domain` veya `Subdomain` |
| `value` | `String` | Varlık değeri (örn. "akbank.com") |
| `metadata` | `Metadata` | Logo/favicon referansları ve ek bilgiler |
| `isActive` | `Boolean` | İzleme aktif mi |
| `createdAt` | `Instant` | — |
| `updatedAt` | `Instant` | — |

**`AssetType` (Sealed ADT):**

| Case | DB Değeri |
|---|---|
| `Domain` | `"domain"` |
| `Subdomain` | `"subdomain"` |

**Davranış:** `deactivate(updatedAt)` — asset'i pasif yapar, yeni kopya döndürür.

---

### 3.4 Exclusion — `Exclusion.scala`

Kontrol edilmeyecek domain'leri tanımlar (allowlist).
Bir exclusion'a uyan `CandidateDiscovery` kaydı `skip_reason="whitelisted"` ile
discovery tablosunda kalır, `candidates`'a terfi etmez.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `ExclusionId` | Birincil anahtar |
| `entityId` | `Option[EntityId]` | Entity'ye özgü veya global exclusion |
| `value` | `String` | Hariç tutulacak pattern |
| `matchType` | `ExclusionMatchType` | Eşleşme tipi |
| `reason` | `ExclusionReason` | Gerekçe |
| `isActive` | `Boolean` | Aktif mi |
| `createdBy` | `String` | Oluşturan kullanıcı |
| `createdAt` | `Instant` | — |
| `updatedAt` | `Instant` | — |

**`ExclusionMatchType` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `Exact` | Tam eşleşme |
| `RegistrableDomain` | Kayıt edilebilir domain eşleşmesi |
| `SubdomainOf` | Subdomain eşleşmesi |
| `Pattern` | Wildcard/regex eşleşmesi |

**`ExclusionReason`:** Open value object — `"whitelisted"`, `"partner"`, `"internal"` gibi.

---

## 4. Discovery Modülü

**Paket:** `drp.discovery.domain`
**Tablo:** `candidate_discoveries`

Sahte site adaylarının ilk toplandığı staging tablosu. DNS/HTTP kontrolünden geçen
aktif kayıtlar `candidates` tablosuna terfi eder.

### 4.1 CandidateDiscovery — `CandidateDiscovery.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `DiscoveryId` | Birincil anahtar |
| `entityId` | `EntityId` | İlgili korunan marka |
| `assetId` | `Option[AssetId]` | İlgili korunan varlık |
| `value` | `String` | Ham domain değeri |
| `normalizedValue` | `String` | Normalize edilmiş değer (unique key için) |
| `source` | `DiscoverySource` | Kaynak — open value object |
| `dnsStatus` | `DnsStatus` | DNS/HTTP kontrol durumu |
| `httpStatusCode` | `Option[Int]` | Son HTTP yanıt kodu |
| `skipReason` | `Option[SkipReason]` | Atlanma gerekçesi |
| `failedCheckCount` | `Int` | Başarısız kontrol sayısı (backoff için) |
| `lastCheckedAt` | `Option[Instant]` | Son kontrol zamanı |
| `nextCheckAt` | `Option[Instant]` | Sonraki kontrol zamanı (backoff) |
| `createdAt` | `Instant` | — |
| `updatedAt` | `Instant` | — |

**`DnsStatus` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `Pending` | Henüz kontrol edilmedi |
| `Active` | DNS çözümlendi, HTTP yanıt veriyor |
| `Inactive` | Ulaşılamıyor — backoff ile tekrar denenecek |
| `Error` | Kontrol sırasında beklenmedik hata |

**`SkipReason` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `Whitelisted` | Exclusion listesinde eşleşti |
| `Duplicate` | Aynı `normalized_value` zaten mevcut |
| `InvalidFormat` | Geçersiz domain formatı |

**`DiscoverySource`:** Open value object — `"manual"`, `"permutation"`, `"ct_log"`, `"complaint"` gibi.

**Davranışlar:**

| Method | Açıklama |
|---|---|
| `markActive(httpStatusCode, checkedAt)` | DNS/HTTP başarılı → Active |
| `markInactive(now, nextCheckAt)` | Ulaşılamıyor → Inactive + failedCheckCount++ |
| `markError(now, nextCheckAt)` | Hata → Error + failedCheckCount++ |
| `skip(reason, now)` | Atlama gerekçesi set et |
| `canPromote` | `Active` ve `skipReason` yok ise `true` |

**Not:** `failedCheckCount` yalnızca `inactive` veya `error` durumlarında artar.
Exponential backoff: `next_check_at = now + f(failedCheckCount)`

---

## 5. Candidate Modülü

**Paket:** `drp.candidate.domain`
**Tablo:** `candidates`

DNS/HTTP kontrolünden geçmiş, aktif pipeline adayları. Sistemin merkezi nesnesi.

### 5.1 Candidate — `Candidate.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `CandidateId` | Birincil anahtar |
| `entityId` | `EntityId` | Korunan marka |
| `discoveryId` | `DiscoveryId` | Kaynaklandığı discovery kaydı |
| `source` | `CandidateSource` | Kaynak — open value object |
| `value` | `String` | Domain değeri |
| `normalizedValue` | `String` | Normalize değer |
| `status` | `CandidateStatus` | Pipeline durumu |
| `metadata` | `Metadata` | Ek bilgiler |
| `discoveredAt` | `Instant` | İlk tespit zamanı |
| `createdAt` | `Instant` | — |
| `updatedAt` | `Instant` | — |

**`CandidateStatus` (Sealed ADT) — Pipeline Durum Makinesi:**

```
Validated → Crawled → Analyzed → Scored → Reviewed → Closed
                                                    ↘
                                               Eliminated
                                                    ↘
                                                  Error
```

| Case | Açıklama |
|---|---|
| `Validated` | DNS/HTTP kontrolü geçti, crawl bekliyor |
| `Crawled` | Web sayfası indirildi |
| `Analyzed` | Özellikler çıkarıldı, benzerlik hesaplandı |
| `Scored` | Risk skoru hesaplandı |
| `Reviewed` | İnsan incelemesi tamamlandı |
| `Closed` | Vaka kapatıldı |
| `Eliminated` | Sahte değil, pipeline'dan çıkarıldı |
| `Error` | İşlem sırasında hata oluştu |

**Not:** `CandidateStatus`'ta `Whitelisted` yoktur.
Whitelisting `CandidateDiscovery` katmanında (discovery.SkipReason) yönetilir;
candidate'e terfi etmez.

**Davranışlar:**

| Method | Açıklama |
|---|---|
| `advanceTo(nextStatus, now)` | Yalnızca `happyPathTransitions` map'indeki geçişlere izin verir |
| `eliminate(now)` | → `Eliminated` |
| `markError(now)` | → `Error` |

---

## 6. Crawl Modülü

**Paket:** `drp.crawl.domain`
**Tablo:** `crawl_results`

Sahte sitenin izole bir ortamda (browserless / headless browser) fetch edilmesiyle
elde edilen ham veriyi temsil eder. İmmutable — bir kez yazılır, değiştirilmez.

### 6.1 CrawlResult — `CrawlResult.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `CrawlResultId` | Birincil anahtar |
| `candidateId` | `CandidateId` | İlgili aday |
| `httpStatus` | `HttpStatus` | Son HTTP durum kodu |
| `redirectChain` | `RedirectChain` | Yönlendirme zinciri |
| `finalUrl` | `String` | Yönlendirmeler sonrası nihai URL |
| `network` | `NetworkObservation` | IP, ASN, hosting bilgileri |
| `storageRef` | `StorageRef` | `blob_storage`'daki crawl bundle referansı |
| `metadata` | `Metadata` | Ek crawl meta verileri |
| `crawledAt` | `Instant` | Crawl zamanı |
| `createdAt` | `Instant` | — |

**Not:** `updatedAt` yoktur — immutable kayıt.

**Yardımcı Tipler:**

**`HttpStatus`** — 100–599 aralığı validate edilen HTTP durum kodu.

**`RedirectHop`** — Bir yönlendirme adımı: `from`, `to`, opsiyonel `statusCode`.

**`RedirectChain`** — `Vector[RedirectHop]` wrapper. `empty` constant mevcut.

**`NetworkObservation`** — Crawl sırasında toplanan ağ gözlemleri:

| Alan | Açıklama |
|---|---|
| `resolvedIp` | Çözümlenen IP adresi |
| `asn` | Otonom sistem numarası |
| `asnOrg` | ASN sahibi organizasyon |
| `hostingProvider` | Hosting sağlayıcısı |
| `ipCountry` | IP'nin bulunduğu ülke |

---

## 7. Analysis Modülü

**Paket:** `drp.analysis.domain`
**Tablolar:** `page_features`, `candidate_asset_matches`, `detection_signals`

Crawl sonuçlarından özellik çıkarımı ve benzerlik analizi.
İki paralel analiz yürütülür:
- **(a)** Candidate → Asset benzerlik skorları
- **(b)** Sayfanın iç sinyalleri (form, OCR, vb.)

### 7.1 PageFeatures — `PageFeatures.scala`

Ham DOM'dan çıkarılan yapısal özet. Idempotent kayıt:
aynı `crawl_result_id` + `extractor_version` çifti için tek satır yazılır.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `PageFeatureId` | Birincil anahtar |
| `crawlResultId` | `CrawlResultId` | Kaynak crawl |
| `title` | `Option[String]` | Sayfa başlığı |
| `hasForm` | `Boolean` | Form elementi var mı |
| `hasPasswordInput` | `Boolean` | Şifre alanı var mı |
| `brandNameFound` | `Boolean` | Korunan marka adı tespit edildi mi |
| `domSummary` | `Metadata` | DOM özeti (JSONB, max 8KB) |
| `extractorVersion` | `ExtractorVersion` | Çıkarıcı versiyonu |
| `createdAt` | `Instant` | — |

**Not:** Tam DOM içeriği `blob_storage`'da saklanır; `dom_summary` yalnızca
yapısal özet içerir ve 8KB ile sınırlıdır.

---

### 7.2 CandidateAssetMatch — `CandidateAssetMatch.scala`

Adayın resmi bir asset ile benzerlik skoru.
Her `match_type` için ayrı satır yazılır (domain, logo, favicon, DOM).

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `CandidateAssetMatchId` | Birincil anahtar |
| `candidateId` | `CandidateId` | Aday |
| `assetId` | `AssetId` | Karşılaştırılan resmi varlık |
| `crawlResultId` | `Option[CrawlResultId]` | İlgili crawl (opsiyonel) |
| `matchType` | `MatchType` | Benzerlik tipi — open value object |
| `similarityScore` | `SimilarityScore` | 0.0–1.0 benzerlik skoru |
| `details` | `Metadata` | Hesaplama detayları |
| `createdAt` | `Instant` | — |

**`MatchType`:** Open value object — `"domain_similarity"`, `"logo_similarity"`,
`"favicon_similarity"`, `"reference_dom_similarity"` gibi.

**`SimilarityScore`:** 0.0–1.0 aralığında, `Score.create()` ile validate edilir.

---

### 7.3 DetectionSignal — `DetectionSignal.scala`

Sayfanın iç sinyalleri. Asset'e referans vermez; sayfanın kendi
özelliklerini (form, şifre alanı, OCR marka eşleşmesi) skorlar.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `DetectionSignalId` | Birincil anahtar |
| `candidateId` | `CandidateId` | Aday |
| `crawlResultId` | `Option[CrawlResultId]` | İlgili crawl |
| `signalType` | `SignalType` | Sinyal tipi — open value object |
| `score` | `SignalScore` | 0.0–1.0 sinyal gücü |
| `details` | `Metadata` | Sinyal detayları |
| `metadata` | `Metadata` | Ek meta veri |
| `createdAt` | `Instant` | — |

**`SignalType`:** Open value object — `"form_detected"`, `"password_input_detected"`,
`"ocr_brand_match"` gibi.

---

## 8. Risk Modülü

**Paket:** `drp.risk.domain`
**Tablolar:** `risk_scores`, `rule_results`

Ağırlıklı kural tabanlı risk skoru hesaplaması.
`RiskScore` ve tüm `RuleResult`'ları tek bir transaction'da yazılır.

### 8.1 RiskScore — `RiskScore.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `RiskScoreId` | Birincil anahtar |
| `candidateId` | `CandidateId` | Değerlendirilen aday |
| `crawlResultId` | `Option[CrawlResultId]` | İlgili crawl |
| `totalScore` | `TotalScore` | 0.0–1.0 toplam skor |
| `verdict` | `Verdict` | Karar |
| `confidence` | `Option[Confidence]` | Güven skoru (opsiyonel) |
| `reasons` | `Metadata` | Skoru oluşturan gerekçeler özeti |
| `llmSummary` | `Option[String]` | LLM ile üretilen açıklama (MVP Plus) |
| `ruleSetVersion` | `RuleSetVersion` | Hangi kural seti kullanıldı |
| `createdAt` | `Instant` | — |

**`Verdict` (Sealed ADT):**

| Case | Eşik | Açıklama |
|---|---|---|
| `Malicious` | ≥ 0.70 | Kötü amaçlı — aksiyon gerekiyor |
| `Suspicious` | ≥ 0.40 | Şüpheli — inceleme gerekiyor |
| `Clean` | < 0.40 | Temiz |

**Risk Skor Formülü:**

```
score = domain_sim × 0.25
      + logo_sim   × 0.20
      + dom_sim    × 0.15
      + form       × 0.20
      + ocr        × 0.10
```

**`verdictFor(score)`** metodu bu eşikleri otomatik uygular.

**Değer Tipleri:**

| Tip | Açıklama |
|---|---|
| `TotalScore` | 0.0–1.0, toplam risk skoru |
| `Confidence` | 0.0–1.0, güven düzeyi |
| `RuleSetVersion` | Kural seti sürümü (örn. `"v1.0"`) |

---

### 8.2 RuleResult — `RuleResult.scala`

Skoru oluşturan her kuralın ayrı kaydı. Explainability için.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `RuleResultId` | Birincil anahtar |
| `riskScoreId` | `RiskScoreId` | Ait olduğu risk skoru |
| `ruleCode` | `RuleCode` | Kural kodu — open value object |
| `weight` | `RuleWeight` | 0.0–1.0 kural ağırlığı |
| `detail` | `Option[Metadata]` | Kural hesaplama detayı |
| `createdAt` | `Instant` | — |

**`RuleCode`:** Open value object — `"DOMAIN_SIM"`, `"LOGO_SIM"`, `"FORM_DETECTED"` gibi.

---

## 9. Review Modülü

**Paket:** `drp.review.domain`
**Tablo:** `reviews`

Risk skoru hesaplanan adayların insan analist tarafından incelenmesi.
Append-only kayıt: aynı aday için birden fazla review olabilir; en son satır geçerlidir.

### 9.1 Review — `Review.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `ReviewId` | Birincil anahtar |
| `candidateId` | `CandidateId` | İncelenen aday |
| `riskScoreId` | `RiskScoreId` | Dayandığı risk skoru (NOT NULL) |
| `reviewer` | `Reviewer` | Analist kimliği |
| `decision` | `ReviewDecision` | Karar |
| `notes` | `Option[String]` | Analist notları |
| `reviewedAt` | `Instant` | Karar zamanı |
| `createdAt` | `Instant` | — |

**Not:** `riskScoreId` zorunludur (`NOT NULL`). Review her zaman bir risk skoruna dayanır.

**`ReviewDecision` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `Confirmed` | Sahte site teyit edildi — vaka açılacak |
| `FalsePositive` | Yanlış alarm — candidate kapatılır |
| `NeedsMoreInfo` | Ek bilgi bekleniyor |

**`Reviewer`:** Boş olamayan string wrapper — analist ID'si veya kullanıcı adı.

---

## 10. Casework Modülü

**Paket:** `drp.casework.domain`
**Tablolar:** `cases`, `evidence_files`

`Confirmed` karar verilen adaylar için vaka yönetimi ve kanıt dosyaları.

### 10.1 Case — `Case.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `CaseId` | Birincil anahtar |
| `candidateId` | `CandidateId` | İlgili aday |
| `reviewId` | `ReviewId` | Vakayı açan review |
| `status` | `CaseStatus` | Vaka durumu |
| `priority` | `Option[CasePriority]` | Öncelik |
| `takedownSentAt` | `Option[Instant]` | Takedown isteği gönderilme zamanı |
| `notes` | `Option[String]` | Notlar |
| `createdAt` | `Instant` | — |
| `updatedAt` | `Instant` | — |

**`CaseStatus` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `Open` | Vaka açık |
| `TakedownRequested` | Takedown isteği gönderildi |
| `Closed` | Vaka kapatıldı |
| `FalsePositive` | Sonradan yanlış alarm olarak işaretlendi |

**`CasePriority` (Sealed ADT):**

| Case | Değer |
|---|---|
| `Low` | `"low"` |
| `Medium` | `"medium"` |
| `High` | `"high"` |

**Davranışlar:**

| Method | Açıklama |
|---|---|
| `requestTakedown(now)` | → `TakedownRequested`, `takedownSentAt` set et |
| `close(now)` | → `Closed` |
| `markFalsePositive(now)` | → `FalsePositive` |

---

### 10.2 EvidenceFile — `EvidenceFile.scala`

Kanıt dosyası referansları. `case_id` FK **yoktur**;
`case → candidateId → evidence_files` zinciriyle erişilir.

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `EvidenceFileId` | Birincil anahtar |
| `candidateId` | `CandidateId` | İlgili aday |
| `crawlResultId` | `Option[CrawlResultId]` | Kaynaklandığı crawl |
| `fileType` | `EvidenceFileType` | Dosya tipi |
| `storageRef` | `StorageRef` | `blob_storage` referansı |
| `contentHash` | `Option[ContentHash]` | Dosya hash'i |
| `capturedAt` | `Instant` | Yakalanma zamanı |
| `createdAt` | `Instant` | — |

**`EvidenceFileType` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `Screenshot` | Sayfa ekran görüntüsü |
| `HtmlArchive` | Ham HTML arşivi |
| `DomSnapshot` | DOM ağacı snapshot'ı |
| `OcrOutput` | OCR çıktısı |
| `Favicon` | Favicon dosyası |
| `Logo` | Logo dosyası |

---

## 11. Platform / Storage Modülü

**Paket:** `drp.platform.storage.domain`
**Tablo:** `blob_storage`

Binary içeriklerin (HTML, screenshot, DOM, OCR, favicon) metadata manifest'i.
Gerçek binary verisi PostgreSQL `bytea` alanında veya ileride S3'te saklanır;
ana tablolar `storage_ref` ile referans verir.

### 11.1 BlobObject — `BlobObject.scala`

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | `BlobStorageId` | Birincil anahtar |
| `storageRef` | `StorageRef` | Erişim referansı |
| `fileType` | `BlobFileType` | Dosya tipi |
| `contentType` | `ContentType` | MIME tipi (örn. `"image/png"`) |
| `sizeBytes` | `Long` | Boyut (negatif olamaz) |
| `contentHash` | `Option[ContentHash]` | Duplikasyon tespiti için hash |
| `compression` | `CompressionType` | Sıkıştırma yöntemi |
| `createdAt` | `Instant` | — |

**`BlobFileType` (Sealed ADT):**

| Case | Açıklama |
|---|---|
| `HtmlArchive` | HTML dosyası |
| `Screenshot` | Ekran görüntüsü |
| `DomSnapshot` | DOM snapshot |
| `OcrOutput` | OCR sonucu |
| `Favicon` | Favicon |
| `Logo` | Logo |
| `CrawlBundle` | Tüm crawl çıktılarının paketi |

**`CompressionType` (Sealed ADT):**

| Case | Değer |
|---|---|
| `NoCompression` | `"none"` |
| `Gzip` | `"gzip"` |

---

## 12. Pipeline Akışı

Aşağıdaki diyagram Akbank örneğinde domain objelerinin birbirini nasıl izlediğini gösterir:

```
Entity[Akbank] + Asset[akbank.com]
        │
        ▼
CandidateDiscovery[pending]     ← permütasyon / manual / şikayet
        │
        ├── Exclusion eşleşmesi → skip(Whitelisted)   → staging'de kalır
        ├── DNS/HTTP başarısız  → markInactive()       → backoff + retry
        │
        └── DNS/HTTP başarılı   → markActive()
                │
                ▼
        Candidate[validated]    ← discoveries'tan terfi
                │
                ▼  (crawl_queue)
        CrawlResult + BlobObject[html/dom/screenshot/favicon]
        Candidate[crawled]
                │
                ▼  (feature_extraction_queue)
        PageFeatures[dom_summary, hasForm, hasPasswordInput]
                │
                ▼  (similarity_queue — 2 paralel bacak)
        ┌───────────────────────┐
        │ CandidateAssetMatch   │  domain_sim(0.88)
        │                       │  logo_sim(0.91)
        │                       │  favicon_sim(0.94)
        │                       │  dom_sim(0.82)
        ├───────────────────────┤
        │ DetectionSignal       │  form_detected(1.0)
        │                       │  password_input(1.0)
        │                       │  ocr_brand_match(0.95)
        └───────────────────────┘
        Candidate[analyzed]
                │
                ▼  (risk_scoring_queue — tek transaction)
        RiskScore[total=0.91, verdict=Malicious]
        RuleResult × 5          ← her kural ayrı satır
        Candidate[scored]
                │
                ▼  (Twirl review ekranı)
        Review[confirmed, analyst_01]
        Candidate[reviewed]
                │
                ▼
        Case[takedown_requested]
        EvidenceFile × 4        ← screenshot, html, dom, ocr
        Candidate[closed]  ✓
```
