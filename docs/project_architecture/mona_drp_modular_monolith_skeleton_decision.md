# Mona DRP — Modular Monolith Skeleton Decision

## 1. Amaç

Bu doküman, mevcut Play Framework Todo uygulamasının Mona DRP projesine modüler monolit yaklaşımıyla nasıl genişletileceğini tanımlar.

Amaç, projeyi microservice mimarisine bölmek değildir. Tek Play uygulaması, tek deployable artifact ve tek veritabanı korunacaktır. Ancak DRP kodları mevcut `controllers`, `services`, `repositories`, `domain` ve `persistence` klasörlerinin içine dağınık şekilde eklenmeyecektir. Bunun yerine DRP kendi ana paketi altında toplanacaktır.

```text
app/
  drp/
```

Ana prensip:

```text
Modül önce, teknik katman sonra.
```

Yani her DRP modülü kendi iş sorumluluğunu temsil edecek; gerekli olduğunda kendi `domain`, `application`, `infrastructure`, `web` ve `workers` katmanlarını kendi içinde taşıyacaktır.

---

## 2. Mimari Karar

Mona DRP için seçilen yapı 4. yaklaşımdır.

Seçilen hedef yapı:

```text
app/
  drp/
    shared/
    asset/
    discovery/
    candidate/
    crawl/
    analysis/
    risk/
    review/
    casework/
    platform/
      storage/
      queue/
```

Bu yapı production açısından daha dengelidir. Çünkü hem domain sınırlarını korur hem de MVP aşamasında gereksiz microservice veya aşırı parçalanmış klasör karmaşası oluşturmaz.

Bu kararda `crawling` yerine `crawl` ismi tercih edilmiştir. Çünkü veri modelindeki `crawl_results` tablosu ile daha tutarlı, daha kısa ve daha anlaşılırdır.

---

## 3. Neden Bu Yapı?

DRP düz bir CRUD uygulaması değildir. Uçtan uca çalışan bir tespit pipeline’ı vardır:

```text
Korunan varlık tanımlanır
→ ham aday keşfedilir
→ aday doğrulanır
→ candidate pipeline'a alınır
→ site crawl edilir
→ sinyaller çıkarılır
→ risk skoru hesaplanır
→ insan incelemesi yapılır
→ case/evidence paketi oluşur
```

Bu nedenle bütün controller’ları tek klasöre, bütün servisleri tek klasöre, bütün repository’leri tek klasöre koymak uzun vadede sınırları bulanıklaştırır.

Seçilen yapı şu avantajları sağlar:

- DRP kodları mevcut Todo kodundan ayrılır.
- Her modül kendi sorumluluğunu taşır.
- `storage` ve `queue` gibi altyapı servisleri business modüllerinden ayrılır.
- `crawl`, `analysis`, `risk`, `review` ve `casework` birbirine karışmaz.
- Fiziksel iskelette hedef modüllerin tamamı açılarak modül sınırları baştan görünür hale getirilir; ancak bu aşamada kod dosyası oluşturulmaz.
- İleride Kafka, MinIO/S3 veya ayrı worker yapısına geçiş kolaylaşır.

---

## 4. Modül Sorumlulukları

### 4.1 `drp.shared`

Ortak primitive ve küçük yardımcı yapıların bulunduğu modüldür.

İçerebilir:

```text
DomainError
ServiceResult
Clock
AuditInfo
ID / value type'lar
Normalization helper'ları
Enum codec helper'ları
```

Bu modül business logic taşımaz. `shared` klasörü zamanla her şeyin atıldığı bir çöplük modüle dönüştürülmemelidir.

---

### 4.2 `drp.asset`

Korunan marka, kurum, kişi ve dijital varlıkların yönetildiği modüldür.

Sorumlulukları:

```text
Entity yönetimi
Asset yönetimi
Asset group yönetimi
Exclusion / allowlist yönetimi
Aktif / pasif asset takibi
```

Bu modül şu soruya cevap verir:

```text
Sistem neyi koruyor?
```

---

### 4.3 `drp.discovery`

Candidate öncesi ham keşif ve staging modülüdür.

Sorumlulukları:

```text
Manual input alma
Permutation çıktısını alma
Dış kaynaklardan gelen adayları staging'e alma
normalized_value üretme
duplicate kontrolü
exclusion kontrolü
DNS/HTTP validation
inactive/error adaylar için recheck planlama
aktif adayları candidate'a promote etme
```

Kritik karar:

```text
Hiçbir input doğrudan candidates tablosuna yazılmamalıdır.
Manual URL dahil bütün girişler önce discovery staging üzerinden geçmelidir.
```

---

### 4.4 `drp.candidate`

Analiz pipeline’ına alınmış gerçek adayların yönetildiği modüldür.

Sorumlulukları:

```text
Candidate oluşturma
Candidate status lifecycle yönetimi
Discovery ile candidate bağını koruma
Pipeline milestone geçişlerini yönetme
error / eliminated / closed gibi terminal durumları yönetme
```

Temel status akışı:

```text
validated → crawled → analyzed → scored → reviewed → closed
```

Not: İlk kararda `candidate` altında `web/` açmak zorunlu değildir. Ancak ileride candidate liste/detay ekranı gerekirse `candidate/web/` eklenebilir.

---

### 4.5 `drp.crawl`

Şüpheli sitenin açılması ve ham teknik gözlemlerin toplanmasından sorumludur.

Sorumlulukları:

```text
Siteyi izole ortamda açma
HTTP status toplama
Redirect chain toplama
Final URL tespiti
IP / ASN / hosting bilgisi toplama
HTML / DOM / screenshot / favicon çıktısı üretme
Crawl job worker'larını yönetme
```

Crawler karar üretmez; sadece gözlem ve ham kanıt üretir.

---

### 4.6 `drp.analysis`

Crawl çıktısından sinyal ve benzerlik sonuçları üreten modüldür.

Sorumlulukları:

```text
DOM feature extraction
Domain similarity
Logo/favicon similarity
OCR brand match
Form/login detection
Detection signal üretimi
Candidate asset match üretimi
```

Bu modül ham dosyayı ana tablolara gömmez. Ham içerikleri storage üzerinden okur, küçük ve sorgulanabilir analiz sonuçlarını DB’ye yazar.

---

### 4.7 `drp.risk`

Risk skoru ve kural motorunun yönetildiği modüldür.

Sorumlulukları:

```text
Analiz sinyallerini okuma
Rule-based scoring çalıştırma
Toplam skor üretme
Verdict üretme
Rule breakdown yazma
Açıklanabilir karar üretme
```

Kritik karar:

```text
LLM yardımcı olabilir ama ana karar motoru LLM'e bağlı olmamalıdır.
Ana karar açıklanabilir rule-based scoring üzerinden verilmelidir.
```

---

### 4.8 `drp.review`

İnsan analist kararlarının yönetildiği modüldür.

Sorumlulukları:

```text
Scored adayları incelemeye sunma
Analist kararını alma
confirmed / false_positive / needs_more_info kararlarını saklama
reviewed status geçişini yönetme
```

Bu modül MVP için önemlidir. Çünkü false positive riskini azaltan human-in-the-loop adımı burada temsil edilir.

---

### 4.9 `drp.casework`

Case, aksiyon ve evidence package mantığının yönetildiği modüldür.

Sorumlulukları:

```text
Confirmed review sonrası case açma
Evidence package oluşturma
Case durumlarını yönetme
Takedown mock/log akışını yönetme
Case anındaki kanıt setini sabitleme
```

`case` Scala keyword olduğu için paket adı olarak `casework` tercih edilmiştir.

---

### 4.10 `drp.platform.storage`

Büyük dosya ve kanıt içeriklerinin saklandığı altyapı modülüdür.

Sorumlulukları:

```text
HTML archive saklama
DOM snapshot saklama
Screenshot saklama
Favicon/logo saklama
OCR output saklama
storage_ref üretme
PostgreSQL bytea implementasyonu
İleride S3/MinIO geçişini soyutlama
```

Kritik karar:

```text
HTML, DOM, screenshot, OCR output gibi büyük içerikler JSONB alanlara gömülmeyecektir.
Ana tablolar sadece storage_ref taşıyacaktır.
```

---

### 4.11 `drp.platform.queue`

Asenkron iş kuyruğu soyutlamasının bulunduğu altyapı modülüdür.

Sorumlulukları:

```text
JobQueue interface
Queue isimleri
PGMQ implementasyonu
Worker mesaj modeli
Retry / fail / complete davranışları
İleri faz Kafka geçişini kolaylaştırma
```

Kritik karar:

```text
Business kodu doğrudan PGMQ fonksiyonlarına bağlanmayacaktır.
Bütün queue işlemleri JobQueue interface üzerinden yapılacaktır.
```

---

## 5. Modül İçi Standart Yapı

Genel modül içi yapı şu şekilde olacaktır:

```text
module/
  domain/
  application/
    ports/
  infrastructure/
  web/
  workers/
```

Her modülde her klasör zorunlu değildir.

| Klasör | Sorumluluk |
|---|---|
| `domain/` | Saf domain nesneleri, value object’ler, status tipleri, domain kuralları |
| `application/` | Use-case servisleri, service interface/impl, port interface’leri |
| `application/ports/` | Repository, storage, queue veya dış servis gibi outbound port/interface tanımları |
| `infrastructure/` | Slick repository, table definitions, mapper, external client, adapter |
| `web/` | Controller, form, request/response DTO, Twirl view model |
| `workers/` | Akka/queue consumer, background job worker |

Temel bağımlılık yönü:

```text
web/workers → application → domain
infrastructure → application ports
```

Domain katmanı Play, Slick, HTTP, JSON veya DB detaylarını bilmemelidir.

---

## 6. Hedef Tam İskelet

Hedefteki tam fiziksel yapı şu şekildedir:

```text
app/
  drp/
    shared/
      domain/
      application/
      infrastructure/

    asset/
      domain/
      application/
        ports/
      infrastructure/
      web/

    discovery/
      domain/
      application/
        ports/
      infrastructure/
      workers/
      web/

    candidate/
      domain/
      application/
        ports/
      infrastructure/
      # web/ ileride candidate liste/detay ekranı gerekirse eklenebilir

    crawl/
      domain/
      application/
        ports/
      infrastructure/
      workers/

    analysis/
      domain/
      application/
        ports/
      infrastructure/
      workers/

    risk/
      domain/
      application/
        ports/
      infrastructure/
      workers/

    review/
      domain/
      application/
        ports/
      infrastructure/
      web/

    casework/
      domain/
      application/
        ports/
      infrastructure/
      web/

    platform/
      storage/
        application/
          ports/
        infrastructure/

      queue/
        application/
          ports/
        infrastructure/
```

Bu yapı nihai hedefi gösterir. Ancak ilk aşamada bütün modüllerin fiziksel olarak açılması zorunlu değildir.

---

## 7. Fiziksel İskelet

İlk plan, yalnızca DRP’nin foundation modüllerini fiziksel olarak açmak ve pipeline modüllerini ihtiyaç ortaya çıktıkça eklemekti. Ancak mevcut aşamada hocaya hedef mimariyi eksiksiz gösterebilmek ve modül sınırlarını baştan görünür hale getirmek için Section 6’daki tam hedef iskelet fiziksel olarak açılmıştır.

Bu karar sadece klasör iskeleti için geçerlidir. Bu aşamada hiçbir Scala kodu, controller, service, repository, table, module veya worker dosyası oluşturulmayacaktır. Klasörler yalnızca `.gitkeep` dosyalarıyla görünür tutulacaktır.

Fiziksel olarak açılan ana DRP modülleri:

```text
app/
  drp/
    shared/
    asset/
    discovery/
    candidate/
    crawl/
    analysis/
    risk/
    review/
    casework/
    platform/
      storage/
      queue/
```

Bu modüller sadece boş ana klasör olarak bırakılmamıştır. Modüler monolit kararının kod yapısında görünür olması için her modülün kendi iç katmanları da oluşturulmuştur.

Fiziksel iskelet şu şekildedir:

```text
app/
  drp/
    shared/
      domain/
      application/
      infrastructure/

    asset/
      domain/
      application/
        ports/
      infrastructure/
      web/

    discovery/
      domain/
      application/
        ports/
      infrastructure/
      workers/
      web/

    candidate/
      domain/
      application/
        ports/
      infrastructure/
      # web/ ileride candidate liste/detay ekranı gerekirse eklenebilir

    crawl/
      domain/
      application/
        ports/
      infrastructure/
      workers/

    analysis/
      domain/
      application/
        ports/
      infrastructure/
      workers/

    risk/
      domain/
      application/
        ports/
      infrastructure/
      workers/

    review/
      domain/
      application/
        ports/
      infrastructure/
      web/

    casework/
      domain/
      application/
        ports/
      infrastructure/
      web/

    platform/
      storage/
        application/
          ports/
        infrastructure/

      queue/
        application/
          ports/
        infrastructure/
```

Bu aşamada modüllerin karşılık geldiği alanlar şunlardır:

```text
shared    → ortak primitive, error/result, helper ve ID tipleri
asset     → entities, asset_groups, assets, exclusions
discovery → candidate_discoveries
candidate → candidates
crawl     → crawl_results ve ham crawl gözlemleri
analysis  → page_features, detection_signals, candidate_asset_matches
risk      → risk_scores ve rule_results
review    → analyst review akışı
casework  → cases ve evidence_files akışı
storage   → blob_storage / storage_ref soyutlaması
queue     → JobQueue interface ve ileride PGMQ implementasyonu
```

Klasörler başlangıçta boş kalacağı için IntelliJ veya dosya sistemi tarafından görünür kalmaları amacıyla her oluşturulan klasöre `.gitkeep` eklenmiştir. Bu dosyalar dışında DRP iskeleti altında kod dosyası bulunmamalıdır.

---

## 8. Bağımlılık Kuralları

Modüller arası bağımlılık genel olarak şu yönde akacaktır:

```text
asset
  ↓
discovery
  ↓
candidate
  ↓
crawl
  ↓
analysis
  ↓
risk
  ↓
review
  ↓
casework
```

Yatay altyapı:

```text
platform.storage → crawl, analysis ve casework tarafından kullanılır
platform.queue   → discovery, candidate, crawl, analysis ve risk tarafından kullanılır
shared           → herkes tarafından sınırlı şekilde kullanılabilir
```

Kritik kurallar:

```text
Controller doğrudan repository çağırmaz.
Bir modül başka modülün repository implementasyonuna doğrudan erişmez.
Modüller arası iletişim application service veya port/interface üzerinden yapılır.
Infrastructure başka modülün domain nesnelerini sahiplenmez.
Domain katmanı Play/Slick/HTTP bağımlılığı taşımaz.
Business kodu doğrudan PGMQ veya blob implementation detayına bağlanmaz.
```

### Port / Interface İletişim Standardı

Modüller arası iletişim iki kanaldan akar:

**1. Asenkron (worker → worker) — PGMQ**

Worker'lar birbirine doğrudan çağrı yapmaz. `JobQueue` interface üzerinden mesaj gönderir.
Mesajlar yalnızca `target_id` ve `job_type` taşır; domain nesnesi taşımaz.

```text
discovery worker → pgmq.enqueue("crawl_queue", {candidate_id})
crawl worker     → pgmq.dequeue → işini yapar → pgmq.enqueue("feature_extraction_queue", ...)
```

**2. Senkron (modül → modül) — Port interface**

Bir modül başka bir modülün tablosuna yazmak veya durumunu değiştirmek istediğinde
o modülün `application/ports/` altında tanımladığı interface'i kullanır.

```text
candidate/application/ports/CandidateLifecycle   ← tüm pipeline bu portu kullanır
candidate/application/ports/CandidatePromotion   ← discovery bu portu kullanır
asset/application/ports/AssetLookup              ← analysis bu portu okuma için kullanır
review/application/ports/CaseCreation            ← review confirmed sonrası casework tetiklenir
platform/storage/application/ports/StorageService ← crawl ve analysis ham dosya saklar
platform/queue/application/ports/JobQueue         ← tüm worker'lar bu interface'i kullanır
```

**Single Writer Prensibi:**

Her tablonun tek bir yazarı vardır. Başka modüller o tabloya yalnızca port üzerinden yazar,
doğrudan SQL/Slick çağrısıyla değil.

```text
candidates tablosu → yalnızca candidate modülü yazar (CandidateLifecycle / CandidatePromotion portları üzerinden)
blob_storage       → yalnızca platform/storage yazar (StorageService portu üzerinden)
```

**Cross-module okuma:**

Bir modülün başka modülün tablosunu okuması port gerektirmez. Monolith avantajı:
okuma doğrudan Slick query ile yapılabilir. Yalnızca yazma ve durum değişikliği port gerektirir.

```text
analysis worker → crawl_results tablosunu doğrudan okuyabilir (READ — port gerekmez)
risk worker     → candidate_asset_matches, detection_signals okuyabilir (READ — port gerekmez)
crawl worker    → candidates.status = "crawled" yapmak için CandidateLifecycle portunu kullanır (WRITE — port zorunlu)
```

---

## 9. Persistence Kararı

Tek PostgreSQL veritabanı kullanılacaktır. Ancak Slick table tanımları tek büyük `Tables.scala` dosyasında biriktirilmeyecektir.

Her modül kendi tablo tanımlarını kendi infrastructure/persistence alanında taşıyacaktır.

Örnek:

```text
asset/infrastructure/persistence/AssetTables.scala
discovery/infrastructure/persistence/DiscoveryTables.scala
candidate/infrastructure/persistence/CandidateTables.scala
crawl/infrastructure/persistence/CrawlTables.scala
analysis/infrastructure/persistence/AnalysisTables.scala
risk/infrastructure/persistence/RiskTables.scala
```

Ortak Slick yardımcıları gerekirse `shared/infrastructure` altında tutulacaktır.

Bu kararın amacı, tek DB kullanırken kod seviyesinde tablo sahipliğini net tutmaktır.

---

## 10. Guice Module Kararı

Root tarafta tek bir ana DRP module olabilir:

```text
app/drp/DrpModule.scala
```

`DrpModule`, alt modülleri install eder.

Örnek alt modüller:

```text
app/drp/asset/AssetModule.scala
app/drp/discovery/DiscoveryModule.scala
app/drp/candidate/CandidateModule.scala
app/drp/crawl/CrawlModule.scala
app/drp/analysis/AnalysisModule.scala
app/drp/risk/RiskModule.scala
app/drp/review/ReviewModule.scala
app/drp/casework/CaseworkModule.scala
app/drp/platform/storage/StorageModule.scala
app/drp/platform/queue/QueueModule.scala
```

Bu sayede mevcut `AppModule` gereksiz şekilde şişmez. Mevcut uygulama modülleri korunur, DRP bağımlılıkları ayrı bir composition root üzerinden yönetilir.

---

## 11. Mevcut Todo Uygulamasına Yaklaşım

Mevcut Todo uygulaması ilk aşamada silinmeyecektir.

Karar:

```text
Todo kodu çalışan referans modül olarak korunur.
DRP kodu app/drp altında ayrı geliştirilir.
Mevcut route/controller/service yapısı bozulmaz.
DRP iskeleti eklendikten sonra baseline migration çıkarılır.
Daha sonra Todo route’larının korunup korunmayacağı ayrıca değerlendirilir.
```

Bu yaklaşım, çalışan baseline’ı bozmadan DRP dönüşümünü güvenli şekilde ilerletmek için tercih edilmiştir.

---

## 12. Sonuç

Seçilen modüler monolit iskeleti 4. yapının düzeltilmiş halidir.

Nihai karar:

```text
4. yapı seçilmiştir.
crawling yerine crawl kullanılacaktır.
storage ve queue platform altında tutulacaktır.
case için casework adı kullanılacaktır.
candidate/web ileride ihtiyaç olursa açılacaktır.
Fiziksel iskelette hedef DRP modüllerinin tamamı açılmıştır; bu aşamada yalnızca `.gitkeep` dosyaları bulunacaktır.
```

Bu yapı, production standartlarına yakın ama MVP için uygulanabilir bir denge sağlar.

Temel prensipler:

```text
Modül önce, katman sonra.
Tek Play uygulaması.
Tek deployable artifact.
Tek PostgreSQL veritabanı.
Domain sınırları net.
Storage ve queue altyapı modülü.
Shared sınırlı.
Mevcut Todo baseline korunur.
```
