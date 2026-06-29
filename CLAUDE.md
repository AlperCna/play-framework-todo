# CLAUDE.md — Mona DRP

**Mona DRP** (Digital Risk Protection), **Play Framework 2.9 / Scala 2.13** üzerinde sıfırdan
geliştirilen **greenfield** bir modüler monolit platformudur. Hedef kod tabanı `app/drp/` altındadır.

Repodaki **`app/todo/`** dizini **iş alanıyla ilgisizdir**. Yalnızca hazır mimari pattern'leri
(Repository, Mapper, pac4j Authentication, Pagination, Slick adapter, Guice modül kompozisyonu)
çalışan bir örnek üzerinden sağlayan **geçici iskeledir** — modüler monolit yapısını otururken
referans modül gibi düşünülmüştür. DRP modülleri bu pattern'leri örnek alır; DRP kendi ayakları
üzerinde durduğunda `todo` iskelesi repodan kaldırılacaktır.

### Dökümanlar (`docs/`)

| Dosya | İçerik |
|---|---|
| `migration_final_schema/migration_final_schema.md` | **Migration öncesi final şema kararları** — 16 tablo; tip/nullable/default/FK/ON DELETE/CHECK/index/trigger. **En güncel şema kaynağı.** |
| `research_documents/Mona_DRP_Happy_Path_v5.md` | MVP Core uçtan uca happy path (pipeline akışı) |
| `research_documents/Mona_DRP_Happy_Path_v5_Veri_Akisi_Simulasyonu.md` | Happy path'in satır-satır DB simülasyonu (Akbank örneği) |
| `research_documents/Mona_DRP_Veri_Modeli_v5_duzeltilmis.pdf` | Veri modeli v5 (düzeltilmiş) |
| `research_documents/Mona_DRP_MVP_Plani.pdf` | MVP planı |
| `research_documents/Mona_DRP_Ileri_Faz_Yol_Haritasi.pdf` | İleri faz yol haritası |
| `project_architecture/current_architecture_map.md` | Mevcut mimari haritası + DRP modül eşlemesi |
| `project_architecture/mona_drp_modular_monolith_skeleton_decision.md` | Modüler monolit iskelet kararı |
| `project_architecture/todo_modular_monolith_migration.md` | Todo iskelesinden çıkarılan pattern dersleri |
| `drp-local-setup.md` | Yerel geliştirme ortamı kurulumu (Docker + migration) |
| `acik-kararlar.md` | **Açık / netleşmemiş teknik kararlar** (ör. worker runtime) — netleşince CLAUDE.md/constitution'a taşınır |

---

## 1. Projenin Amacı (MVP)

**Deadline: Ağustos 2026.**

> "Korunan bir markaya benzeyen sahte siteyi bul, siteyi güvenli şekilde incele,
> domain/görsel/DOM/form/OCR sinyallerini çıkar, açıklanabilir risk skoru üret,
> insan incelemesine sun ve aksiyon için kanıt paketi hazırla."

Demo senaryosu: **Akbank** (`akbank.com`) korunan marka, `akbank-guvenli-giris.com` sahte site.

### Kapsam Ayrımı

| Kapsam | Açıklama |
|---|---|
| **MVP Core** | Pipeline'ın çalışması için zorunlu 13 feature |
| **MVP Plus** | Zaman kalırsa eklenen 6 güçlendirici feature |
| **Demo/Mock** | Takedown gerçek API değil, log/mock |
| **İleri Faz** | Threat Graph, multi-tenant, sosyal medya, dark web vb. |

---

## 2. Mevcut Repo Durumu (Doğrulanmış)

Repo iki kod tabanı içerir: çalışan **`app/todo/`** iskelesi (modüler monolit pattern referansı —
geçici, kaldırılacak) ve **`app/drp/`** hedef modülleri (feature-by-feature inşa edilir; modüller boş
`.gitkeep` ya da kısmen kodlanmış olabilir — güncel durum için dizin ağacına / aktif feature bloğuna bak).

### Tech Stack

| Katman | Teknoloji |
|---|---|
| Framework | Play Framework 2.9, Scala 2.13.18 |
| ORM | Slick 5 (`play-slick`) |
| DB | MS SQL Server (T-SQL) → **PostgreSQL'e taşınacak** |
| Auth | pac4j `play-pac4j` + FormClient + cookie session (ShiroAesDataEncrypter) |
| DI | Guice (Google Inject) |
| Test | ScalaTest + scalatestplus-play |
| Actor | Akka Typed (`CompletedTaskCleaner`) |
| View | Twirl server-rendered (**React yok, böyle kalacak**) |
| Migration | Manuel versiyonlu SQL — `app/migrations/drp-postgres/V001..V006` (16 tablo) |
| Local Dev | Docker Compose (`ghcr.io/pgmq/pg18-pgmq:v1.10.0`, port 55432) — `scripts/setup.ps1` / `setup.sh` |

### Mevcut Paket Yapısı

```text
app/
├── todo/                 ← GEÇİCİ İSKELE (pattern referansı; iş alanıyla ilgisiz, kaldırılacak)
│   ├── shared/           ← AuditInfo/Clock/ServiceResult/Page + CrudRepository, BaseTables,
│   │                       Tables (facade), RowMapper, SlickCrudSupport, persistence modülleri
│   ├── task/             ← {domain, application, infrastructure, web} — örnek modül (Akka cleaner dahil)
│   ├── category/         ← {domain, application, infrastructure, web}
│   ├── user/             ← {domain, application, infrastructure, web} — pac4j auth + SecurityModule
│   └── boot/             ← AppModule (tüm modülleri compose eden tek giriş noktası)
│
├── drp/                  ← HEDEF DRP KODU (feature-by-feature dolar; modül boş veya kodlanmış olabilir)
│   ├── shared/           → ortak primitive, error/result, helper, ID tipleri
│   ├── asset/            → entities, asset_groups, assets, exclusions
│   ├── discovery/        → candidate_discoveries
│   ├── candidate/        → candidates
│   ├── crawl/            → crawl_results
│   ├── analysis/         → page_features, candidate_asset_matches, detection_signals
│   ├── risk/             → risk_scores, rule_results
│   ├── review/           → reviews
│   ├── casework/         → cases, evidence_files
│   └── platform/
│       ├── storage/      → blob_storage (StorageService interface)
│       └── queue/        → JobQueue interface → PGMQ impl
│
├── filters/             ← AccessLogFilter (cross-cutting)
└── migrations/
    ├── initialize.sql   ← todo iskelesinin SQL Server baseline'ı
    └── drp-postgres/    ← DRP PostgreSQL migration seti (V001..V006)
```

Her DRP modülü kendi içinde `domain / application / application/ports / infrastructure / web / workers`
katmanlarını taşır (hepsi her modülde zorunlu değil). Detay:
`docs/project_architecture/mona_drp_modular_monolith_skeleton_decision.md`.

### Todo İskelesinden Çıkarılan Modülerlik Dersleri

`app/todo/` modüler monolite çevrildi; geriye DRP modüllerini yazarken kaçınılacak 4 ders kaldı
(detay: `docs/project_architecture/todo_modular_monolith_migration.md`):

| # | Ders | Todo'daki örnek konum |
|---|---|---|
| 1 | Domain sınıfı başka modülün domain'ini import etmemeli (primitive / `shared` ile konuş) | `app/todo/task/domain/TaskItem.scala` (→ `Category` import) |
| 2 | Modüller arası erişim port üzerinden; cross-module repository DI'ı yapma | `app/todo/task/application/TaskItemServiceImpl.scala` (→ `CategoryRepository` DI) |
| 3 | Tek `Tables.scala` facade'ı gizli bağımlılık üretir; tablo sahipliği modülde kalmalı | `app/todo/shared/infrastructure/Tables.scala` |
| 4 | Cross-module FK'da tek sahip belirle (sahip yazar, diğerleri port'tan okur) | `task_item_categories` (DB şema) |

### Güvenlik Notu

`conf/application.conf` hâlâ todo iskelesinin SQL Server bağlantısını ve **düz metin SA şifresini**
taşıyor:
```
slick.dbs.default.db.password = "BP4329H42Jk#"
```
Bu satır todo iskelesi kaldırılınca gidecek; o güne kadar repoda düz metin sır bulundurmamak için
env variable'a (`SLICK_DB_PASSWORD`) taşınmalı. DRP tarafı zaten PostgreSQL'e `.env` üzerinden
bağlanır (bkz. `.env.example`, `docs/drp-local-setup.md`).

---

## 3. Hedef Mimari: Modüler Monolit

### Bounded Context → Modül Eşlemesi

```
app/drp/
  asset/             → entities, asset_groups, assets, exclusions
  discovery/         → candidate_discoveries
  candidate/         → candidates
  crawl/             → crawl_results
  analysis/          → page_features, candidate_asset_matches, detection_signals
  risk/              → risk_scores, rule_results
  review/            → reviews
  casework/          → cases, evidence_files
  platform/storage/  → blob_storage  (StorageService interface)
  platform/queue/    → JobQueue interface → PGMQ impl
```

### Kritik Altyapı Değişiklikleri

> Bu, kalıcı **yön** haritasıdır (todo → DRP hedefi). Hangisinin *tamamlandığı* anlık bir durumdur →
> burada tutulmaz; güncel durum için `specs/` ve dizin ağacına bak.

| Şu an (todo / SQL Server) | Hedef (DRP) |
|---|---|
| MS SQL Server | **PostgreSQL** + `slick-pg` (JSONB için zorunlu) |
| Migration yok | **Manuel, versiyonlu PostgreSQL SQL dosyaları** (`app/migrations/drp-postgres`) |
| Docker yok | `ghcr.io/pgmq/pg18-pgmq:v1.10.0` — `scripts/setup.ps1` ile tek komut |
| Akka actor cleanup | **PGMQ** + `JobQueue` trait (interface soyutlaması zorunlu) |
| 4 tablo (T-SQL) | **16 tablo** (PostgreSQL migration'ları) |
| JSONB yok | `slick-pg` + CHECK constraint'ler |
| `outbox_jobs` yok | PGMQ queues (pgmq extension) |

---

## 4. Veri Modeli v5 (16 Tablo — Özet)

> **Tek doğru kaynak:** `docs/migration_final_schema/migration_final_schema.md` (tip, nullable,
> default, FK, ON DELETE, CHECK, index, trigger kararlarının tamamı + tablo-tablo final şema).
> Aşağısı yalnızca yön içindir; bir çelişki olursa final şema dokümanı geçerlidir.

16 tablo, pipeline katmanlarına göre:

| Katman | Tablolar |
|---|---|
| Varlık / Asset | `entities`, `asset_groups`, `assets`, `exclusions` |
| Aday / Tespit | `candidate_discoveries`, `candidates`, `crawl_results`, `page_features`, `candidate_asset_matches`, `detection_signals` |
| Karar / Aksiyon | `risk_scores`, `rule_results`, `reviews`, `cases`, `evidence_files` |
| Altyapı | `blob_storage` |

**Akılda tutulacak şema kararları:**

- **Mutable** (`created_at` + `updated_at`, trigger ile): `entities`, `asset_groups`, `assets`, `exclusions`, `candidate_discoveries`, `candidates`, `cases`. Diğer tüm tablolar **immutable / append-only** (yalnızca `created_at`).
- Tüm FK'lar **ON DELETE RESTRICT** — MVP'de cascade delete yok (kanıt/iz zinciri korunur).
- PostgreSQL ENUM yok; kritik lifecycle alanları **TEXT + CHECK** (`candidates.status`, `risk_scores.verdict`, `reviews.decision`, `cases.status`, `assets.asset_type`, …). `source` / `match_type` / `signal_type` / `rule_code` gibi genişleyebilir alanlarda CHECK yok.
- Skor alanları **NUMERIC(5,4)** + `CHECK 0..1` (`similarity_score`, `score`, `total_score`, `confidence`, `weight`).
- `candidates.discovery_id` **NOT NULL** — manuel dahil her input önce `candidate_discoveries` staging'inden geçer; hiçbir aday doğrudan `candidates`'a yazılmaz.
- `candidates.status` içinde **`whitelisted` YOK** (staging'de `skip_reason='whitelisted'`); `candidates.asset_id` **YOK** (kaynak asset'e `discovery_id → candidate_discoveries.asset_id` ile gidilir).
- `evidence_files` **`case_id` taşımaz**; kanıt paketi `case → candidate_id → evidence_files` ile türetilir.
- Büyük içerik (HTML/DOM/screenshot/OCR) JSONB'ye gömülmez → `blob_storage` (bytea, STORAGE EXTERNAL) + `storage_ref`.
- **JSONB/TEXT boyut CHECK'leri MVP migration'ında YOK** — gerçek veri gözlemlendikten sonra ayrı bir `V007` hardening migration'ına ertelendi. Yalnızca `redirect_chain` için `jsonb_typeof = 'array'` (boyut değil, veri bütünlüğü) CHECK'i vardır.

**storage_ref formatı:** `pg://evidence/{candidate_id}/{crawl_run_id}/{file_type}.{ext}` (MVP, `blob_storage`) → ileride `s3://mona-drp/evidence/...`.

---

## 5. Happy Path Pipeline (v5 — Akbank Örneği)

> Tam akış + satır-satır DB simülasyonu: `docs/research_documents/Mona_DRP_Happy_Path_v5.md` ve
> `Mona_DRP_Happy_Path_v5_Veri_Akisi_Simulasyonu.md`. Aşağısı özet.

Korunan marka **Akbank** (`akbank.com`), sahte site `akbank-guvenli-giris.com`. Pipeline öncesi:
entity/asset tanımı → `candidate_discoveries` staging (exclusion + DNS/HTTP) → aktif aday
`candidates`'a terfi. Sonrası asenkron worker zinciri; aday şu status milestone'larından geçer
(her ok bir PGMQ mesajı, her durak kalıcı kanıt yazar):

```text
validated ─crawl→ crawled ─extract+analyze→ analyzed ─score→ scored ─insan→ reviewed ─case→ closed
   │            │                  │                        │            │           │
crawl_queue  crawl_results   candidate_asset_matches     risk_scores  reviews     cases
             blob_storage    detection_signals           rule_results             evidence_files
                             page_features
```

Özet: **topla → iki açıdan analiz et (asset benzerliği + sayfa-içi sinyal) → tek skora indir →
insana onaylat → vaka aç ve kanıtı bağla.**

**Risk skor formülü:** `domain_sim×0.25 + logo_sim×0.20 + dom_sim×0.15 + form×0.20 + ocr×0.10`,
normalize = ham_toplam / 0.90 (fingerprint 0.10 Plus'ta gelecek). Eşik:
`≥0.70 malicious`, `≥0.40 suspicious`, else `clean`.

---

## 6. PGMQ / JobQueue Mimarisi

### Queue'lar

| Queue | İş |
|---|---|
| `candidate_validation_queue` | DNS/HTTP kontrolü → aktif olanı `candidates`'a terfi et |
| `crawl_queue` | Crawler işleri |
| `feature_extraction_queue` | `page_features` çıkarımı |
| `similarity_queue` | domain/logo/favicon/DOM similarity (post-crawl) |
| `risk_scoring_queue` | Risk skoru + rule breakdown |

### Temel Kurallar

- Uygulama kodu `JobQueue` **interface'ine** bağlı; PGMQ'ya doğrudan bağlanmaz.
- Mesajlar yalnızca `target_type`, `target_id`, `job_type` + küçük parametreler taşır. HTML/DOM/screenshot **payload'a girmez**.
- Worker'lar **idempotent** — aynı mesaj iki kez işlense bile duplicate oluşmaz.
- `JobQueue` interface: `enqueue / dequeue / complete / fail / metrics`. İleride Kafka implementasyonu iş mantığını değiştirmez.

```scala
trait JobQueue {
  def enqueue(queue: String, payload: Json): Future[JobId]
  def dequeue(queue: String, count: Int, vt: Duration): Future[List[Job]]
  def complete(queue: String, jobId: JobId): Future[Unit]
  def fail(queue: String, jobId: JobId, error: String): Future[Unit]
  def metrics(queue: String): Future[QueueMetrics]
}
// MVP: PgmqJobQueue  |  İleride: KafkaJobQueue
```

### StorageService Interface

```scala
trait StorageService {
  def store(candidateId: Long, crawlRunId: Long, fileType: String, data: Array[Byte], contentType: String): Future[StorageRef]
  def retrieve(ref: StorageRef): Future[Array[Byte]]
  def delete(ref: StorageRef): Future[Unit]
}
// MVP: PostgresBlobStorage  |  İleride: MinioStorage / S3Storage
```

---

## 7. MVP Core Backlog (13 Madde — Pipeline Sırasıyla)

| # | Feature | Yazan Tablo(lar) |
|---|---|---|
| 1 | Protected Entity / Asset | `entities`, `assets` |
| 2 | Manual URL Input | `candidate_discoveries` (source=manual) → DNS/HTTP geçince `candidates` |
| 3 | Domain Variation (Permütasyon) | `candidate_discoveries` (source=permutation) → DNS/HTTP geçince `candidates` |
| 4 | DNS / HTTP Kontrol | `candidate_discoveries` (dns_status, failed_check_count) → aktif olunca `candidates`'a terfi |
| 5 | Crawler | `crawl_results` (ham fetch), `blob_storage` (html/dom/screenshot bundle) |
| 6 | Domain Similarity | `candidate_asset_matches` (match_type=domain_similarity) |
| 7 | Basic DOM Structural Similarity | `candidate_asset_matches` (match_type=reference_dom_similarity) |
| 8 | Logo / Favicon Similarity | `candidate_asset_matches` (logo_similarity, favicon_similarity) |
| 9 | OCR | `detection_signals` (ocr_brand_match), `blob_storage` (ocr_output) |
| 10 | Form / Login Detection | `page_features` (has_form, has_password_input), `detection_signals` |
| 11 | Rule-Based Risk Scoring | `risk_scores`, `rule_results` (tek transaction) |
| 12 | Human Review | `reviews`, `candidates.status` |
| 13 | Evidence Package / Takedown Mock | `cases`, `evidence_files` |

## 7.1 MVP Plus Backlog (6 Madde — Bağımsız)

CT Log Feed, WHOIS Feed, Şikayetvar Scraping, HTML Fingerprint, LLM Risk Özeti, Basit Recurrence Monitoring.

## 7.2 Teknik Foundation (Önkoşul — Core'dan Önce)

> Core'dan önce gereken foundation parçaları (kalıcı liste). Hangisinin *tamamlandığı* anlıktır →
> `specs/` ve dizin ağacından oku.

1. PostgreSQL migration dosyaları (16 tablo + FK + index) — `app/migrations/drp-postgres/V001..V006`
2. Repository / DAO katmanı (Slick)
3. `JobQueue` interface + PGMQ implementasyonu
4. `StorageService` interface + `PostgresBlobStorage`
5. Demo seed datası (entity, asset, candidate)
6. Docker Compose (`ghcr.io/pgmq/pg18-pgmq:v1.10.0`) — `scripts/setup.ps1` / `setup.sh` ile tek komut kurulum
7. Env variable geçişi — `.env` + `.env.example`; `.gitignore`'a eklendi

---

## 8. MVP Dışında Bırakılanlar (Kodlanmayacak)

Kafka, Full Threat Graph, multi-tenant, subdomain discovery, iç sayfa crawling, email security,
sosyal medya/reklam/dark web izleme, gerçek takedown entegrasyonu, real-time koruma,
deepfake, self-improving model / Agentic SOC.

---

## 9. Teknik Bileşenler

| Bileşen | Teknoloji | Not |
|---|---|---|
| Backend/API | Scala Play Framework | Server-rendered; React yok |
| Template engine | Play Twirl | Human review ekranı dahil |
| Actor/worker | *(açık karar)* | Worker yürütme runtime'ı netleşmedi → `docs/acik-kararlar.md`; mesajlaşma `JobQueue`/PGMQ arkasında (§6) |
| DB | PostgreSQL + `slick-pg` | DRP hedef DB (todo hâlâ SQL Server — bkz §2); JSONB + pgmq için |
| Mesaj kuyruğu | PGMQ (pgmq extension) | `JobQueue` interface arkasında |
| Dosya depolama | PostgreSQL `bytea` / `blob_storage` | `StorageService` interface arkasında |
| Crawler | Browserless.io veya izole Docker/VM | Ana sunucudan izole |
| Domain similarity | Levenshtein, Jaro-Winkler | Scala veya Python |
| Logo/favicon similarity | pHash / imagehash | Hafif görsel benzerlik |
| OCR | Tesseract veya Google Vision API | |
| LLM özeti | OpenAI API veya muadili | MVP Plus; karar motoru değil |

---

## 9.1 View / Web Katmanı Konvansiyonları

> **Kural kaynağı:** Constitution **Madde I** — `web` katmanı ince; controller iş kararı vermez,
> repository'ye dokunmaz (yalnızca `application` servisi); Twirl view = **typed view-model'in saf
> render'ı**, domain/persistence tipi sızmaz. Aşağısı bunun **"nasıl"ı**; referans implementasyon
> çalışır halde `app/todo/.../web/`. Yeni DRP web feature'ları bu kalıbı izler.

- **Controller:** `MessagesAbstractController(cc)`'tan türet, i18n `request.messages("key")`. Servis
  sonucunu HTTP + view'a map et:
  `Right(_) → Redirect(...).flashing("success" -> msg)` · `Left(DomainError.NotFound(..)) → NotFound(msg)` ·
  `Left(err) → BadRequest(view(form.withGlobalError(messages(err.code))))`.
- **View-model:** Controller domain entity'sini view'a **doğrudan vermez** — `web` paketinde bir
  view-model'e map eder, Twirl'e yalnızca onu (+ primitive / `Form` / `Call`) geçirir. Write tarafı
  zaten `*FormData` (örn. `TaskItemFormData`); read tarafı için `*ViewModel` kur.
  ⚠ Mevcut `todo` ve `drp/asset` view'ları bu kuralı **henüz ihlal ediyor** (view'a
  `Seq[...domain.Category]` / `Seq[...domain.Exclusion]`) — kopyalama, yeni kodda view-model kullan.
- **Form + validation:** `web` paketinde `*FormData` + Play `Form`;
  `bindFromRequest().fold(formWithErrors => BadRequest(view), data => …)`. Domain hatası global error'a:
  `form.fill(data).withGlobalError(messages(err.code))`, view `@if(form.hasGlobalErrors)` ile gösterir.
- **CSRF (mevcut kalıp, araştırma değil):** Play default filtreleri açık
  (`conf/application.conf`'ta `play.filters.enabled += …`, `=` değil → CSRF korunur). Her
  `@helper.form { … }` içine `@helper.CSRF.formField`.
- **Twirl şablonu:** Konum `app/<...>/<module>/web/views/*.scala.html`, çağrı
  `<module>.web.views.html.<isim>`. İlk satır typed `@(…)` parametreleri; şablonda business logic yok.
- **Layout / partial:** Ortak base layout (`@main(title){ … }`, todo'da `todo.shared.web.views.html.main`)
  + yeniden kullanılan partial (`paginationView`). DRP'nin henüz ortak layout'u yok — ilk DRP web
  feature'ı `drp/shared/web/views/main` kurar (şu an `exclusions.scala.html` HTML'i inline yazıyor; kopyalama).
- **Routes:** Tek `conf/routes` dosyası; DRP route'ları `/drp/<module>/...` prefix + yorum bloklarıyla
  gruplanır (modül başına ayrı dosya yok).
- **Auth:** Korumalı controller `AuthenticatedAction` (`authAction { … }` / `.async`); kullanıcıya
  `request.user` (`AuthenticatedRequest`) üzerinden erişilir.
- **Açık karar (henüz standart yok):** `DomainError`'ı global yerine **field-level** form hatasına eşleme.


## 9.2 Scala Kod Konvansiyonları (domain-bağımsız)

> Veri modelleme **MUST'ları constitution'da** (Additional Constraints → Domain bullet:
> ADT/`sealed`/illegal-states/`Either`/smart constructor). Burası onların uygulama notu + ek stil.
> **Kurallar** = always/never (zorlayıcı). **Heuristik** = yalnızca tercih; istisnası gerekçeyle
> serbest, MUST diye encode etme.

**Kurallar (always / never)**

- **Structural recursion:** ADT üzerindeki fonksiyon her case'i ele alır; `sealed` sayesinde derleyici
  eksik case'i yakalar — kod verinin şeklini takip eder.
- **`map` / `flatMap` / for-comprehension ile zincirle** — nested `try`/`if` yok. ⚠ for-comprehension
  **fail-fast**'tir (ilk `Left`'te durur). Birden çok alan hatasını **birden** göstermen gerekiyorsa
  (form validation) bu yetmez → Cats `Validated` (`Ior`).
- **Trait `default` implementasyonu TÜM alt tipler için doğru olmalı;** değilse default verme.
- **Genişletme type class ile** (sahip olmadığın tipe davranış → ad-hoc polymorphism). **Instance
  paketleme:** tek instance + tipin sahibi sensin → `companion object`; çok instance ya da tipin sahibi
  değilsin → ayrı obje + explicit import.
- **Implicit conversion YASAK** (`implicit def A => B` yok; `import scala.language.implicitConversions`
  görürsen dur). Enrichment → `implicit class`; type class → `implicit val` / `implicit parameter`.
- **`case class` + `copy`** standart veri taşıyıcı; **`companion.apply`** = factory / smart constructor.

**Heuristik (tercih — zorlama yok)**

- **Önce modeli ands/ors olarak çiz** (BNF benzeri); ADT mekanik olarak ondan türesin.
- **Polymorphism mi, pattern match mi:** metot yalnızca sınıfın kendi alanlarına bağlıysa → sınıf içinde;
  dış veriye bağlı ya da çok implementasyon → harici objede `match`. ("Tek implementasyonda base trait'te
  match kazanır" bir tercih, kanun değil — OO-eğilimli ekipler davranışı tipte tutar.)
- **`fold`:** ADT'ne bir `fold` (katamorfizma) **sağla**; ama her fonksiyonu fold ile yazma —
  okunabilirlik düşer.
- **Generics mi, trait/case class mı:** kalıcı/isimli/tekrar kullanılan kavram → trait/case class;
  hızlı tek seferlik → `Tuple` / `Option` / `Either`.
- **OO ↔ FP ekseni (expression problem):** yeni **veri** sık eklenecekse OO (davranışı tipte tut); yeni
  **davranış** sık eklenecekse `sealed` + `match`. Hangi eksen sık değişiyorsa ona göre seç.

> Not: hepsi Scala **2.13** biçimi; 3'e geçilirse ADT → `enum`, type class → `given` / `using`.

---

## 10. Çalışma Kuralları

- **Önce keşfet → plan sun → onay al → uygula.** Büyük tek seferlik refactor yapma.
- **Her adımda `test/` altındaki ScalaTest suite'leri geçmeye devam etmeli.**
- **Modül parçalaması modül modül** — atomik, bağımsız commit'ler.
- **Migration stratejisi**: Play Evolutions/Flyway kullanılmaz; DRP PostgreSQL şeması manuel çalıştırılan `app/migrations/drp-postgres/V001..V006` dosyalarıyla yönetilir.
- **Todo baseline notu**: mevcut `app/migrations/initialize.sql` korunur; DRP migration seti ayrı PostgreSQL hedef şemasıdır.
- Kod yorumu (Constitution V): public soyutlama (paket/dosya/trait/public metot) imzadan anlaşılmıyorsa kısa bir **WHAT** ("ne yapar") yorumu taşır; **inline** yorum yalnız non-obvious **WHY** için — HOW'un satır-satır anlatımı yok.
- JSONB içine büyük içerik (HTML/DOM/screenshot) gömme → `blob_storage`.
- Pipeline worker'ları idempotent yaz.
- Liste okuması varsayılan **sayfalı** (`Page`/`PageRequest`); unbounded `SELECT *` yalnız küçük/sabit-sınırlı kümede + gerekçeyle (Constitution IV).
- Her yeni repository **port'u** için bir **in-memory test adapter** yaz (servis DB'siz testlenebilsin); onsuz eklemek gerekçe ister (Constitution Dev-Workflow).

---

## 11. Netleşen Kararlar

- **Migration aracı**: Play Evolutions/Flyway yok; manuel, versiyonlu SQL dosyaları kullanılacak.
- **Migration konumu**: DRP PostgreSQL dosyaları `app/migrations/drp-postgres/` altında tutulacak.
- **Şema sahipliği**: MVP'de tek PostgreSQL şeması ve açık FK'lar kullanılacak; modül sınırı kod paketleri ve port'lar üzerinden korunacak.

> Henüz **netleşmemiş** kararlar (kesin olmayan teknik seçimler) ayrı tutulur: `docs/acik-kararlar.md` — netleşince buraya/constitution'a taşınır.

<!-- SPECKIT START -->
_Aktif feature yok — `/speckit-specify` ile başlat._
<!-- SPECKIT END -->
