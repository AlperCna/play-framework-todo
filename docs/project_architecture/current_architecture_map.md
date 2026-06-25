# Current Architecture Map — Modular Monolith Transition

## 1. Amaç

Bu dokümanın amacı, mevcut `play-framework-todo` projesinin bugünkü mimari yapısını çıkarmak ve bu yapının Mona DRP projesine modüler monolit mimarisiyle nasıl dönüştürüleceğini tanımlamaktır.

Bu aşamada amaç doğrudan kod yazmak veya bütün veri modelini migration dosyasına dökmek değildir. Önce mevcut projenin hangi katmanlardan oluştuğunu, bu katmanların DRP tarafında nasıl korunacağını ve yeni DRP modüllerinin hangi sınırlara göre ayrılacağını netleştirmektir.

Mevcut proje çalışan bir Play Framework uygulamasıdır. Bu nedenle dönüşüm stratejisi “sıfırdan proje açmak” değil, çalışan Play uygulamasını bozmadan DRP domain’ini kontrollü şekilde eklemek olacaktır.

---

## 2. Mevcut Projenin Durumu

Mevcut proje bir Todo uygulaması olarak çalışmaktadır.

Doğrulanan durum:

* `sbt clean compile` başarılı.
* Testler başarılı.
* `sbt run` ile uygulama 9000 portunda ayağa kalkmaktadır.
* Proje Play Framework Scala uygulamasıdır.
* Dependency Injection için Guice kullanılmaktadır.
* Persistence katmanında Slick kullanılmaktadır.
* Mevcut veritabanı konfigürasyonu Microsoft SQL Server üzerindedir.
* Authentication için pac4j kullanılmaktadır.
* Cleanup gibi arka plan işleri için Akka actor yapısı mevcuttur.
* View katmanı Play/Twirl server-rendered yapıdadır.

Bu durum, Mona DRP için avantajlıdır. Çünkü proje zaten controller, service, repository, module ve actor gibi ayrımlara sahiptir. Modüler monolit dönüşümü bu ayrımların üzerine kurulacaktır.

---

## 3. Mevcut Katmanlı Yapı

Mevcut uygulamada ana klasörler şu sorumluluklara sahiptir:

| Katman         | Mevcut Görevi                                  | DRP’de Kullanım Kararı                                                          |
| -------------- | ---------------------------------------------- | ------------------------------------------------------------------------------- |
| `controllers`  | HTTP isteklerini karşılar                      | DRP controller’ları ayrı modül altında veya ayrı controller paketiyle eklenecek |
| `services`     | İş kurallarını yürütür                         | DRP application service yapısının temel karşılığı olacak                        |
| `repositories` | Veri erişim sözleşmeleri ve implementasyonları | DRP repository interface ve Slick implementasyonları için korunacak             |
| `domain`       | Todo, category, user gibi domain nesneleri     | DRP domain nesneleri ayrı bounded context’lerde tutulacak                       |
| `persistence`  | Slick tablo ve mapper yapıları                 | DRP tabloları için ayrı persistence paketi açılacak                             |
| `modules`      | Guice dependency wiring                        | DRP modülleri burada ayrı Guice module olarak bağlanacak                        |
| `actors`       | Zamanlanmış veya arka plan işleri              | DRP worker/actor işleri için örnek alınacak                                     |
| `forms`        | HTML form verileri                             | DRP Twirl ekranları için gerekirse kullanılacak                                 |
| `views`        | Server-rendered ekranlar                       | Human review ve temel admin ekranları için kullanılacak                         |
| `security`     | Kullanıcı doğrulama                            | DRP ekranları mevcut auth yapısıyla korunacak                                   |
| `actions`      | Authenticated request/action yapısı            | DRP controller’larında tekrar kullanılacak                                      |

---

## 4. Modüler Monolit Kararı

Mona DRP için seçilecek mimari yaklaşım modüler monolittir.

Bu şu anlama gelir:

* Tek uygulama çalışacaktır.
* Tek Play Framework projesi kullanılacaktır.
* Tek deployable artifact üretilecektir.
* Tek PostgreSQL veritabanı kullanılacaktır.
* Modüller ayrı servisler olarak deploy edilmeyecektir.
* Ancak kod içinde domain sınırları net ayrılacaktır.
* Her modül kendi domain, service ve repository sorumluluğuna sahip olacaktır.
* Modüller birbirlerinin tablolarına doğrudan rastgele erişmeyecektir.
* Modüller arası iletişim mümkün olduğunca service interface veya application service üzerinden yapılacaktır.

Bu yaklaşım MVP için uygundur. Çünkü dağıtık sistem karmaşıklığına girmeden, domain sınırları temiz tutulabilir. İleride bazı parçalar ayrıştırılmak istenirse, bu modül sınırları microservice veya worker ayrımına geçişi kolaylaştırır.

---

## 5. Hedef DRP Modül Yapısı

Mona DRP tarafı aşağıdaki modüllerle temsil edilecektir:

```text
app/drp/
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

Bu yapı, veri modelindeki pipeline sırasına göre düşünülmüştür.

---

## 6. DRP Modülleri ve Sorumlulukları

### 6.1 `drp.shared`

Ortak kullanılan temel yapıların bulunduğu modüldür.

İçerik adayları:

* Ortak hata tipleri
* Ortak result yapıları
* Zaman/clock yardımcıları
* JSON helper’ları
* Ortak enum codec mantığı
* Normalization helper’ları
* Ortak repository base trait’leri

Bu modül business logic taşımamalıdır. Sadece diğer modüllerin tekrar etmemesi gereken teknik veya ortak domain yardımcılarını içermelidir.

---

### 6.2 `drp.asset`

Korunan marka, kurum, kişi ve dijital varlıkların yönetildiği modüldür.

Veri modeli karşılığı:

* `entities`
* `asset_groups`
* `assets`
* `exclusions`

Sorumlulukları:

* Korunan entity oluşturma
* Entity altında asset oluşturma
* Asset metadata yönetimi
* Domain/subdomain asset ayrımı
* Exclusion/allowlist yönetimi
* Aktif/pasif asset takibi

Bu modül DRP sisteminin başlangıç noktasıdır. Çünkü aday üretimi, analiz ve risk hesaplama her zaman bir entity veya asset bağlamında yapılacaktır.

---

### 6.3 `drp.discovery`

Ham aday keşiflerinin ve candidate öncesi elemenin yönetildiği modüldür.

Veri modeli karşılığı:

* `candidate_discoveries`

Sorumlulukları:

* Manuel URL girişini staging’e almak
* Permütasyon çıktısını staging’e almak
* CT log / WHOIS / complaint gibi dış kaynaklardan gelen ham adayları staging’e almak
* `normalized_value` üretmek
* Duplicate guard mantığını uygulamak
* Exclusion kontrolünü yapmak
* DNS/HTTP kontrol sonucunu saklamak
* İnaktif adaylar için `next_check_at` ve `failed_check_count` ile recheck planlamak
* Aktif adayları candidate pipeline’a promote etmek

Önemli karar:

Bütün input kaynakları önce `candidate_discoveries` üzerinden geçmelidir. Manuel URL girişi bile doğrudan `candidates` tablosuna yazılmamalıdır. Böylece validation, exclusion ve duplicate kontrolü tek kapıdan uygulanır.

---

### 6.4 `drp.candidate`

Gerçek analiz pipeline’ına alınan adayları yöneten modüldür.

Veri modeli karşılığı:

* `candidates`

Sorumlulukları:

* Promote edilmiş aday kaydını oluşturmak
* Candidate status milestone’larını yönetmek
* `validated → crawled → analyzed → scored → reviewed → closed` akışını korumak
* Hata durumunda `error` statüsüne almak
* Candidate’ın hangi discovery kaydından geldiğini takip etmek
* Pipeline adımları arasında veri bütünlüğünü korumak

Önemli karar:

`candidates` ham permütasyon havuzu değildir. Sadece DNS/HTTP ve exclusion kontrolünden geçmiş, analiz edilmeye değer adayları temsil eder.

---

### 6.5 `drp.crawl`

Şüpheli sitenin güvenli şekilde açılması ve ham gözlemlerin toplanması bu modülün sorumluluğundadır.

Veri modeli karşılığı:

* `crawl_results`
* `page_features` ile ilişkili ilk veri üretimi
* `blob_storage` ile ilişkili ham dosya üretimi

Sorumlulukları:

* Crawl job’unu almak
* Siteyi izole ortamda açmak
* HTTP status, redirect chain, final URL, IP, ASN, hosting provider gibi teknik bilgileri toplamak
* HTML, DOM snapshot, screenshot, favicon gibi ham kanıtları storage katmanına göndermek
* `crawl_results` satırı oluşturmak
* Candidate status’unu `crawled` durumuna taşımak
* Sonraki feature extraction işini kuyruğa atmak

Crawl modülü karar üretmez. Sadece gözlem üretir.

---

### 6.6 `drp.analysis`

Crawl sonrası sinyal ve benzerlik analizlerinin yürütüldüğü modüldür.

Veri modeli karşılığı:

* `page_features`
* `candidate_asset_matches`
* `detection_signals`

Sorumlulukları:

* DOM’dan küçük yapısal özet çıkarmak
* Form ve password input tespiti yapmak
* Marka adı bulunup bulunmadığını çıkarmak
* Domain similarity hesaplamak
* Logo/favicon similarity hesaplamak
* DOM structural similarity hesaplamak
* OCR sonucunu sinyal olarak kaydetmek
* Skorlu sinyalleri `detection_signals` tablosuna yazmak
* Asset karşılaştırmalarını `candidate_asset_matches` tablosuna yazmak

Bu modül ham dosya saklamaz. Ham veriyi storage üzerinden okur, küçük ve sorgulanabilir sonuçları DB’ye yazar.

---

### 6.7 `drp.risk`

Analiz sinyallerinin açıklanabilir risk skoruna dönüştürüldüğü modüldür.

Veri modeli karşılığı:

* `risk_scores`
* `rule_results`

Sorumlulukları:

* Sinyalleri okumak
* Rule-based scoring çalıştırmak
* Hangi kuralın skora katkı verdiğini satır bazında yazmak
* Toplam risk skorunu üretmek
* Verdict üretmek
* Gerekçeleri açıklanabilir şekilde saklamak
* Candidate status’unu `scored` durumuna taşımak

Önemli karar:

Risk skoru yalnızca tek bir sayı değildir. Hangi sinyallerin ve hangi kuralların bu skora neden olduğu izlenebilir olmalıdır.

---

### 6.8 `drp.review`

İnsan analist kararlarının yönetildiği modüldür.

Veri modeli karşılığı:

* `reviews`

Sorumlulukları:

* Skorlanmış adayları review ekranına sunmak
* Analist kararını almak
* Confirmed / false positive / needs more info gibi kararları saklamak
* Candidate status’unu `reviewed` durumuna taşımak
* Review kararını case açma akışına bağlamak

Bu modül otomatik detection’ın insan doğrulamasıyla kapanmasını sağlar.

---

### 6.9 `drp.casework`

Confirmed tehditlerin case ve evidence package seviyesinde yönetildiği modüldür.

Veri modeli karşılığı:

* `cases`
* `evidence_files`

Sorumlulukları:

* Review sonrası case oluşturmak
* Evidence package hazırlamak
* Takedown mock/log akışını yönetmek
* Case durumlarını takip etmek
* Kanıt paketini `case → candidate_id → evidence_files` üzerinden türetmek
* Aksiyon çıktısının hangi skor, hangi review ve hangi candidate evidence set üzerinden üretildiğini izlenebilir kılmak

Bu modül MVP’nin “tespit aksiyona dönüştü” kısmını temsil eder.

---

### 6.10 `drp.platform.storage`

Büyük dosya ve kanıt içeriklerinin saklandığı modüldür.

Veri modeli karşılığı:

* `blob_storage`

Sorumlulukları:

* HTML arşivi saklamak
* DOM snapshot saklamak
* Screenshot saklamak
* Favicon/logo dosyalarını saklamak
* OCR çıktısı gibi büyük text/binary içerikleri saklamak
* `storage_ref` üretmek
* İleride PostgreSQL bytea’dan S3/MinIO’ya geçişi interface arkasında saklamak

Önemli karar:

Büyük veri JSONB alanlara gömülmeyecektir. Ana tablolar sadece `storage_ref` taşır.

---

### 6.11 `drp.platform.queue`

Asenkron pipeline işlerinin soyutlandığı modüldür.

Veri modeli karşılığı:

* Uygulama tablosu olarak `outbox_jobs` yoktur.
* PGMQ, PostgreSQL içinde queue altyapısı olarak kullanılacaktır.

Sorumlulukları:

* `JobQueue` interface tanımlamak
* PGMQ implementasyonu yazmak
* Queue isimlerini merkezi olarak yönetmek
* Worker’lara mesaj sağlamak
* İşlerin tamamlanma/başarısızlık durumunu yönetmek
* İleride Kafka geçişini kolaylaştırmak

Önemli karar:

Uygulama kodu doğrudan PGMQ fonksiyonlarına bağlanmamalıdır. Tüm queue işlemleri `JobQueue` interface üzerinden yürütülmelidir.

---

## 7. Modüller Arası Bağımlılık Kuralları

Modüler monolitin sürdürülebilir olması için modüller arası bağımlılık kuralları net olmalıdır.

Önerilen bağımlılık yönü:

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

Yardımcı modüller:

```text
shared           → herkes kullanabilir
platform.storage → crawl, analysis, casework kullanabilir
platform.queue   → worker tetikleyen application service’ler kullanabilir
```

Yasaklanması gereken durumlar:

* `risk` modülü doğrudan `crawl` repository’sini rastgele update etmemeli.
* `review` modülü `analysis` tablolarını değiştirmemeli.
* `casework` modülü MVP’de case-specific frozen evidence varsaymamalı; kanıt paketini candidate üzerinden türetmeli.
* Controller’lar doğrudan repository çağırmamalı.
* Modüller başka modülün database tablosunu doğrudan sahiplenmemeli.

### Port / Interface İletişim Standardı

Modüller arası iletişim iki kanaldan akar:

**Asenkron (worker → worker):** `JobQueue` interface üzerinden PGMQ mesajı. Mesajlar yalnızca `target_id` taşır.

**Senkron (modül → modül):** Hedef modülün `application/ports/` altında tanımladığı interface üzerinden.

**Single Writer Prensibi:** Her tablonun tek yazarı vardır. Başka modüller yalnızca port üzerinden yazar.

Temel portlar ve kullanıcıları:

| Port | Tanımlayan Modül | Kullanan Modüller |
|---|---|---|
| `CandidateLifecycle` | `candidate` | crawl, analysis, risk, review, casework |
| `CandidatePromotion` | `candidate` | discovery |
| `AssetLookup` | `asset` | analysis |
| `CaseCreation` | `review` | casework |
| `StorageService` | `platform/storage` | crawl, analysis |
| `JobQueue` | `platform/queue` | discovery, crawl, analysis, risk |

Cross-module **okuma** (READ) port gerektirmez — monolith avantajı olarak Slick ile doğrudan sorgu yapılabilir.
Cross-module **yazma / durum değişikliği** (WRITE) her zaman port üzerinden yapılır.

---

## 8. Controller / Service / Repository Standardı

Her DRP modülünde mümkün olduğunca şu yapı kullanılacaktır:

```text
domain/
application/
infrastructure/
web/
```

Önerilen paket anlamları:

| Paket                 | Anlamı                                                                          |
| --------------------- | ------------------------------------------------------------------------------- |
| `domain`              | Entity, value object, domain enum/codec, domain error                           |
| `application`         | Use case/service interface ve iş akışı                                          |
| `application/ports`   | Bu modülün dışarıya açtığı veya dışarıdan ihtiyaç duyduğu port/interface tanımları |
| `infrastructure`      | Slick repository, external client, storage/queue implementasyonu, port adapter  |
| `web`                 | Controller, form, view model                                                    |
| `workers`             | Akka/PGMQ consumer, background job worker                                       |

Örnek:

```text
app/drp/asset/
  domain/
    Entity.scala
    Asset.scala
    Exclusion.scala
  application/
    AssetService.scala
    AssetServiceImpl.scala
    ports/
      AssetLookup.scala       ← analysis modülü bu portu okuma için kullanır
  infrastructure/
    SlickAssetRepository.scala
    AssetTables.scala
  web/
    AssetController.scala

app/drp/candidate/
  domain/
    Candidate.scala
  application/
    ports/
      CandidateLifecycle.scala  ← crawl/analysis/risk/review/casework bu portu kullanır
      CandidatePromotion.scala  ← discovery bu portu kullanır
  infrastructure/
    SlickCandidateRepository.scala
    CandidateLifecycleImpl.scala  ← portları implement eder
    CandidateTables.scala
```

Bu standart, mevcut Todo projesindeki controller-service-repository ayrımını DRP tarafında daha modüler hale getirir.

---

9. Veritabanı ve Migration Stratejisi

DRP dönüşümünde migration stratejisi otomatik migration aracı üzerine kurulmayacaktır.

Bu projede migration için Play Evolutions veya Flyway kullanılmayacaktır. Migration dosyaları repo içinde versiyonlu SQL dosyaları olarak tutulacak, ancak uygulama startup sırasında otomatik migration çalıştırmayacaktır.

Migration dosyaları geliştirici veya operator tarafından manuel olarak çalıştırılacaktır. Böylece veritabanı değişiklikleri uygulama açılışına bağlı kalmadan kontrollü şekilde uygulanabilecektir.

Güncel DRP PostgreSQL migration dosya yapısı:

app/migrations/drp-postgres/
README.md
V001__asset_layer_up.sql
V001__asset_layer_down.sql
V002__discovery_layer_up.sql
V002__discovery_layer_down.sql
V003__storage_layer_up.sql
V003__storage_layer_down.sql
V004__pipeline_layer_up.sql
V004__pipeline_layer_down.sql
V005__decision_layer_up.sql
V005__decision_layer_down.sql
V006__pgmq_queues_up.sql
V006__pgmq_queues_down.sql

Bu yapıda:

V001 asset katmanını kurar: entities, asset_groups, assets, exclusions.
V002 discovery/candidate katmanını kurar: candidate_discoveries, candidates.
V003 storage katmanını kurar: blob_storage.
V004 pipeline gözlem/analiz katmanını kurar: crawl_results, page_features, candidate_asset_matches, detection_signals.
V005 karar/aksiyon katmanını kurar: risk_scores, rule_results, reviews, cases, evidence_files.
V006 PGMQ queue kurulumunu ayrı tutar.
README.md migration dosyalarının hangi sırayla ve nasıl manuel çalıştırılacağını açıklar.

Bu kararın nedeni, migration sürecini uygulama lifecycle’ından ayırmaktır. Uygulama ayağa kalkarken otomatik tablo oluşturmayacak veya değiştirmeyecektir. Veritabanı değişiklikleri açık SQL dosyaları üzerinden manuel ve izlenebilir şekilde uygulanacaktır.

10. DRP Veri Modeli ile Modül Eşleşmesi
    Veri Modeli Tablosu	Sahip Modül
    entities	drp.asset
    asset_groups	drp.asset
    assets	drp.asset
    exclusions	drp.asset
    candidate_discoveries	drp.discovery
    candidates	drp.candidate
    crawl_results	drp.crawl
    page_features	drp.analysis
    candidate_asset_matches	drp.analysis
    detection_signals	drp.analysis
    risk_scores	drp.risk
    rule_results	drp.risk
    reviews	drp.review
    cases	drp.casework
    evidence_files	drp.casework
    blob_storage	drp.platform.storage

Not: outbox_jobs tablosu oluşturulmayacaktır. Asenkron iş akışı uygulama tablosu yerine PGMQ + JobQueue interface yaklaşımıyla tasarlanacaktır. PGMQ queue kurulumu DRP migration setinde ayrı V006 dosyası olarak tutulur.

11. İlk Dönüşüm Aşamaları
    Aşama 1 — Mevcut Projeyi Sabitleme

Bu aşamada mevcut Todo uygulamasının çalışan hali bozulmadan belgelenir.

Yapılan veya yapılacak işler:

Proje compile edilir.
Testler çalıştırılır.
sbt run ile uygulamanın ayağa kalktığı doğrulanır.
Mevcut app ve conf klasör ağaçları çıkarılır.
Mevcut controller, service, repository, domain, persistence, module ve actor yapısı belgelenir.
Mevcut app/migrations/initialize.sql dosyası incelenir.
Mevcut SQL Server şeması anlaşılır.

Bu aşamada DRP tablosu eklenmez. Amaç önce başlangıç noktasını netleştirmektir.

Aşama 2 — Modüler Monolit İskeleti

Bu aşamada DRP için kod sınırları hazırlanır, ancak business logic yazılmaz.

Yapılacak işler:

app/drp ana paketi açılır.
DRP alt modül klasörleri oluşturulur.
Her modül için domain, application, infrastructure, web ayrımı değerlendirilir.
Mevcut Todo koduna dokunulmaz.
Todo uygulaması geçici olarak çalışan referans modül gibi korunur.

Önerilen DRP modül yapısı:

app/drp/
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

Bu aşamanın amacı, modüler monolit sınırlarını görünür hale getirmektir. Henüz migration veya repository implementasyonu yazılmaz.

Aşama 3 — Manuel DRP PostgreSQL Migration Seti

Bu aşamada Mona DRP için PostgreSQL hedef şeması versiyonlu ve manuel çalıştırılan SQL dosyaları haline getirilir.

Yapılacak işler:

Mevcut `app/migrations/initialize.sql` korunur.
DRP PostgreSQL şeması `app/migrations/drp-postgres/` altında katmanlı dosyalara ayrılır.
Her migration dosyası kendi `BEGIN` / `COMMIT` bloğunu taşır.
README.md içinde manuel migration çalıştırma ve rollback sırası açıklanır.
Seed/demo data migration dosyalarına eklenmez.

Bu aşamanın amacı, Todo uygulamasının mevcut SQL Server başlangıç dosyasını bozmadan DRP PostgreSQL hedef şemasını ayrı ve izlenebilir bir migration seti olarak kurmaktır.

Aşama 4 — DRP PostgreSQL Geçiş Kararı ve Yeni Migrationlar

Bu aşamada Mona DRP için PostgreSQL hedef şeması hazırlanır.

Yapılacak işler:

Mevcut Todo SQL Server başlangıç dosyası ile DRP PostgreSQL hedef şeması ayrı tutulur.
DRP’nin PostgreSQL gerektiren özellikleri belirlenir:
JSONB
partial index
bytea tabanlı blob storage
PGMQ entegrasyonuna uygun queue yaklaşımı
storage_ref standardı
`V001..V006` DRP PostgreSQL migration dosyaları hazırlanır.
Down dosyaları ters bağımlılık sırasına göre yazılır.
DRP tabloları foreign key bağımlılık sırasına göre oluşturulur.
outbox_jobs tablosu eklenmez.

DRP tablo oluşturma sırası genel olarak şu şekilde olacaktır:

entities
asset_groups
assets
exclusions
candidate_discoveries
candidates
blob_storage
crawl_results
page_features
candidate_asset_matches
detection_signals
risk_scores
rule_results
reviews
cases
evidence_files

Bu aşamada amaç bütün business logic’i yazmak değil, DRP veri modelini veritabanı seviyesinde ayağa kaldırmaktır.

Aşama 5 — DRP Foundation Kodları

Migration dosyaları hazırlandıktan sonra DRP kod iskeleti doldurulur.

Yapılacak işler:

Domain class’ları yazılır.
Repository interface’leri yazılır.
Slick repository implementasyonları yazılır.
Service interface ve service implementasyonları yazılır.
Guice module binding yapılır.
Basit seed/demo veri akışı hazırlanır.

Öncelikli modüller:

drp.asset
drp.discovery
drp.candidate
drp.platform.storage

Bunlar tamamlanmadan crawl, analysis ve risk modüllerine geçilmez.

Aşama 6 — Pipeline Modülleri

Foundation tamamlandıktan sonra DRP pipeline adımları sırayla geliştirilir:

Discovery input
DNS/HTTP validation
Candidate promotion
Crawl
Feature extraction
Similarity analysis
Risk scoring
Human review
Case/evidence package

Bu sıralama veri modelindeki gerçek akışla uyumludur.

12. Açık Mimari Kararlar

Aşağıdaki kararlar proje ilerlemeden netleştirilmelidir.

Karar 1 — Todo modülü korunacak mı?

Seçenekler:

A: Todo modülü geçici olarak korunur, DRP yanında geliştirilir.
B: Todo modülü tamamen kaldırılır.
C: Todo modülü örnek/legacy modül olarak tutulur ama route’lardan çıkarılır.

Geçici öneri: A veya C.

Karar 2 — DRP paketleri app/drp/... altında mı açılacak?

Seçenekler:

A: Tüm DRP modülleri app/drp altında toplanır.
B: Mevcut yapı gibi domain, services, repositories ana klasörleri altında DRP alt paketleri açılır.

Geçici öneri: A. Çünkü modüler monolit sınırları daha net olur.

Karar 3 — Migration stratejisi ne olacak?

Karar:

Play Evolutions kullanılmayacak.
Flyway kullanılmayacak.
Migration dosyaları `app/migrations/drp-postgres/` altında manuel çalıştırılan versiyonlu SQL dosyaları olarak tutulacak.
Uygulama startup sırasında otomatik migration çalıştırmayacak.

Bu karar kesinleşmiştir.

Karar 4 — Todo baseline mı, DRP PostgreSQL seti mi?

Karar:

Mevcut Todo `app/migrations/initialize.sql` dosyası korunacak.
DRP PostgreSQL şeması ayrı `app/migrations/drp-postgres/` migration seti olarak yönetilecek.
DRP tabloları katmanlı V001..V006 dosyalarında ele alınacak.

Bu karar, mevcut çalışan uygulamanın SQL Server başlangıcını bozmadan DRP hedef şemasını net ve ayrı tutmak için alınmıştır.

Karar 5 — Todo’nun mevcut SQL Server yapısı ne zaman PostgreSQL’e taşınacak?

Seçenekler:

A: Todo tabloları da PostgreSQL’e taşınır.
B: Todo persistence kaldırılır, sadece DRP PostgreSQL modeli kalır.
C: Mevcut SQL Server başlangıcı korunur, DRP PostgreSQL hedefi ayrı migration seti olarak ele alınır.

Güncel karar: C.

Karar 6 — PGMQ hemen kurulacak mı?

Seçenekler:

A: İlk DRP migration ile PGMQ kurulumu da eklenir.
B: Önce DRP tabloları kurulur, PGMQ ayrı setup adımı olur.
C: İlk aşamada sadece JobQueue interface yazılır, PGMQ implementasyonu sonra bağlanır.

Güncel karar: A, ancak PGMQ kurulumu DRP tablo migration'larından ayrı `V006__pgmq_queues_up/down.sql` dosyalarında tutulur.

Karar 7 — UI tarafında ne kadar Twirl ekranı yapılacak?

Seçenekler:

A: Sadece human review ekranı yapılır.
B: Entity/asset yönetimi için de basit ekran yapılır.
C: İlk aşamada sadece endpoint/repository test edilir, UI sonra yapılır.

Geçici öneri: B. Çünkü Play/Twirl zaten mevcut projede kullanılmaktadır.

13. Encoding Notu

Markdown dosyaları UTF-8 olarak kaydedilmelidir.

Türkçe karakterlerin bozulmaması için özellikle Windows ortamında şu noktalara dikkat edilmelidir:

VS Code kullanılıyorsa sağ alttan encoding kontrol edilmelidir.
Gerekirse Save with Encoding -> UTF-8 seçilmelidir.
PowerShell çıktıları dosyaya yazılırken encoding kontrol edilmelidir.
Türkçe karakterler AmaÃ§, dokÃ¼man, modÃ¼ler gibi görünüyorsa dosya encoding’i düzeltilmelidir.
14. Sonuç

Mevcut Todo uygulaması, Mona DRP için kullanılabilecek iyi bir başlangıç iskeletidir. Proje zaten Play Framework, Guice module yapısı, Slick repository ayrımı, service katmanı, actor ve Twirl view yapısı içermektedir.

Dönüşüm stratejisi şu sırayla ilerleyecektir:

Çalışan todo uygulamasını bozma
→ Mevcut mimariyi belgeleyerek sabitle
→ Modüler monolit sınırlarını belirle
→ Mevcut SQL Server initialize.sql dosyasını koru
→ DRP PostgreSQL schema migration setini app/migrations/drp-postgres altında hazırla
→ DRP foundation kodlarını yaz
→ Pipeline modüllerini sırayla geliştir

Bu doküman, modüler monolit dönüşümü için ilk mimari referans olarak kullanılacaktır.
