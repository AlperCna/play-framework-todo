# CLAUDE.md — Mona DRP

Bu repo, **Play Framework 2.9 / Scala 2.13** üzerinde geliştirilmiş bir todo uygulamasından
**Mona DRP** (Digital Risk Protection) platformuna dönüştürülmektedir.

Dökümanlar: `docs/` klasörü. Güncel belgeler: `Mona_DRP_Data_Model_v5_Corrected.docx`,
`Mona_DRP_Happy_Path_v5.md`, `Mona_DRP_Happy_Path_Data_Flow_Simulation.md`,
`Mona_DRP_MVP_Plan_Final.pdf`, `Mona_DRP_Backlog_v2.docx`, `Scalability_Fixes.md`,
`Postgres_Extension_Research_2.md`.

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
| Migration | Manuel versiyonlu SQL — `app/migrations/drp-postgres/V001..V006` (✅ tamamlandı, 16 tablo) |
| Local Dev | Docker Compose (`ghcr.io/pgmq/pg18-pgmq:v1.10.0`, port 55432) — `scripts/setup.ps1` / `setup.sh` |

### Mevcut Paket Yapısı

```
app/
├── domain/               ← Saf domain; IO yok. Smart ctor, ADT, Either.
│   ├── task/             ← TaskItem (rich), Urgency ADT, TaskItemCategory
│   ├── category/         ← Category
│   ├── user/             ← User
│   └── common/           ← AuditInfo, AuditableEntity, DomainError, Priority
├── services/             ← interface + impl (ServiceResult mini-EitherT)
├── repositories/
│   ├── interfaces/       ← port'lar (CrudRepository, TaskItemRepository…)
│   ├── sql/              ← Slick adapter'ları (SlickCrudSupport mixin)
│   └── inmemory/         ← test adapter'ları
├── persistence/db/       ← Slick tablo tanımları + Row sınıfları
│   ├── Tables.scala      ← FACADE: with UsersTable with CategoriesTable …
│   ├── BaseTables.scala  ← profile DI + BaseTable[R]
│   └── mappers/          ← RowMapper[Domain, Row] implicit instance'ları
├── controllers/          ← Play HTTP; form parse → service → Twirl/JSON
├── modules/
│   ├── AppModule.scala   ← ROOT: Clock + backend seçimi + context install
│   ├── SecurityModule.scala
│   ├── CleanupModule.scala
│   ├── context/          ← TaskModule, CategoryModule, UserModule
│   └── persistence/      ← SlickPersistenceModule, InMemoryPersistenceModule
├── actors/               ← CompletedTaskCleaner (Akka Typed, zamanlanmış)
├── actions/              ← AuthenticatedAction (pac4j)
├── filters/              ← AccessLogFilter
├── forms/                ← Form data sınıfları
├── pagination/           ← Page, PageRequest, PageWindow
└── views/                ← Twirl şablonları
```

### Modüler Monolit Geçişini Engelleyen 4 Yapışma Noktası

| # | Sorun | Dosya |
|---|---|---|
| 1 | `TaskItemServiceImpl` doğrudan `CategoryRepository` enjekte ediyor | `app/services/TaskItemServiceImpl.scala:25` |
| 2 | `domain.task.TaskItem` → `domain.category.Category` import | `app/domain/task/TaskItem.scala:5` |
| 3 | `Tables.scala` tüm tabloları tek facade'da birleştiriyor | `app/persistence/db/Tables.scala` |
| 4 | `task_item_categories` cross-module FK (tasks ↔ categories) | DB şema |

### Güvenlik Notu (Öncelikli)

`conf/application.conf` içinde SA şifresi düz metin commit'li:
```
slick.dbs.default.db.password = "BP4329H42Jk#"
```
→ `SLICK_DB_PASSWORD` env variable'ına taşınmalı.

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

| Şu an / Durum | Hedef | Durum |
|---|---|---|
| MS SQL Server | **PostgreSQL** + `slick-pg` (JSONB için zorunlu) | ⬜ Slick bağlantısı bekliyor |
| Migration yok | **Manuel, versiyonlu PostgreSQL SQL dosyaları** (`app/migrations/drp-postgres`) | ✅ V001–V006 tamamlandı |
| Docker yok | `ghcr.io/pgmq/pg18-pgmq:v1.10.0` — `scripts/setup.ps1` ile tek komut | ✅ Tamamlandı |
| Akka actor cleanup | **PGMQ** + `JobQueue` trait (interface soyutlaması zorunlu) | ⬜ Bekliyor |
| 4 tablo (T-SQL) | **16 tablo** (PostgreSQL migration'ları) | ✅ 16 tablo aktif |
| JSONB yok | `slick-pg` + CHECK constraint'ler | ⬜ Slick layer bekliyor |
| `outbox_jobs` yok | PGMQ queues (pgmq extension) | ✅ 5 queue hazır |

---

## 4. Veri Modeli v5 (Güncel Şema — 16 Tablo)

### 4.1 Tablo Özeti

**Varlık ve Asset Katmanı**

| Tablo | Görev | Tip |
|---|---|---|
| `entities` | Korunan marka/kurum/kişi | mutable |
| `asset_groups` | Entity altında opsiyonel alt gruplama | mutable |
| `assets` | Korunan dijital varlık (domain/subdomain). Logo/favicon ayrı tip değil; `metadata` JSONB'de referans. | mutable |
| `exclusions` | Allowlist — kontrol edilmeyecek domain'ler. `skip_reason="whitelisted"` ile `candidate_discoveries`'ta kalır. | mutable |

**Aday ve Tespit Katmanı**

| Tablo | Görev | Tip |
|---|---|---|
| `candidate_discoveries` | Ham permütasyon staging tablosu. Exclusion + DNS/HTTP kontrolü burada. Duplicate guard: `UNIQUE(entity_id, normalized_value)`. | mutable |
| `candidates` | DNS/HTTP'den geçmiş gerçek adaylar. Status: `validated→crawled→analyzed→scored→reviewed→closed` + `eliminated`, `error`. | mutable |
| `crawl_results` | Site açılmadan üretilemeyen FETCH verisi. `storage_ref` → `blob_storage` manifest. | immutable |
| `page_features` | Ham DOM'dan çıkarılan yapısal özet. Idempotent: `UNIQUE(crawl_result_id, extractor_version)`. | immutable |
| `candidate_asset_matches` | Adayın resmi asset'e benzerlik skorları (domain/logo/favicon/dom). | immutable |
| `detection_signals` | Sayfanın iç sinyalleri (form, password_input, ocr_brand_match). Asset'e referans vermez. | immutable |

**Karar ve Aksiyon Katmanı**

| Tablo | Görev | Tip |
|---|---|---|
| `risk_scores` | Ağırlıklı toplam skor + verdict (`clean/suspicious/malicious`). `rule_results` ile tek transaction'da yazılır. | immutable |
| `rule_results` | Skoru oluşturan her kuralın katkısı (FK → `risk_scores`). | immutable |
| `reviews` | İnsan onay kararı. Append-only; en son satır geçerli. | immutable/append-only |
| `cases` | Onaylı tehdit için vaka kaydı. Takedown mock/log. | mutable |
| `evidence_files` | Kanıt dosya referansları. `case_id` YOK; `case → candidate_id → evidence_files`. | immutable |

**Altyapı**

| Tablo | Görev | Tip |
|---|---|---|
| `blob_storage` | HTML/DOM/screenshot/OCR binary içerik (bytea, STORAGE EXTERNAL). Ana tablolar şişmez; `storage_ref` ile erişim. | immutable/append-only |

### 4.2 Önemli Şema Kararları

- `candidates.asset_id` **YOK** — kaynak asset'e `discovery_id → candidate_discoveries.asset_id` ile gidilir.
- `failed_check_count` yalnızca `inactive/error` denemelerde artar. Exponential backoff: `next_check_at = now() + f(failed_check_count)`.
- JSONB boyut limitleri CHECK constraint ile zorunlu: `dom_summary < 8KB`, `redirect_chain < 4KB`, `details < 4KB`, `metadata < 2KB`, `llm_summary < 5000 char`.
- `candidates` index: partial — sadece aktif pipeline adayları (`WHERE status IN ('validated','crawled','analyzed','scored',...)`).
- `evidence_files(content_hash)` index: partial — `WHERE content_hash IS NOT NULL`.

### 4.3 storage_ref Formatı

```
pg://evidence/{candidate_id}/{crawl_run_id}/{file_type}.{ext}   ← MVP (blob_storage)
s3://mona-drp/evidence/{candidate_id}/{crawl_run_id}/...        ← İleride
```

---

## 5. Happy Path Pipeline (v5 — Akbank Örneği)

```
entities[Akbank] + assets[akbank.com]
        ↓
candidate_discoveries[pending] ← permütasyon/manual/complaint
        ↓ exclusion + DNS/HTTP kontrol
  whitelisted → staging'de kalır (skip_reason)
  inactive   → backoff ile recheck (next_check_at)
  active     → candidates'a TERFİ
        ↓
candidates[validated] → crawl_queue
        ↓ Crawl worker (izole ortam)
crawl_results + blob_storage (html/dom/screenshot/favicon + crawl_bundle manifest)
candidates[crawled] → feature_extraction_queue
        ↓ Feature extraction worker
page_features (dom_summary JSONB <8KB; tam DOM blob'da)
        → similarity_queue
        ↓ Analiz (2 paralel bacak)
  (a) candidate_asset_matches: domain_sim(0.88) + logo_sim(0.91) + favicon_sim(0.94) + dom_sim(0.82)
  (b) detection_signals: form_detected(1.0) + password_input_detected(1.0) + ocr_brand_match(0.95)
candidates[analyzed] → risk_scoring_queue
        ↓ Risk scoring (tek transaction)
risk_scores[total=0.91, verdict=malicious] + rule_results[5 kural]
candidates[scored]
        ↓ Human review (Twirl ekranı)
reviews[confirmed, analyst_01]
candidates[reviewed]
        ↓ Case + takedown mock
cases[takedown_requested] + evidence_files[4 dosya referansı]
candidates[closed] ✓
```

### Risk Skor Formülü

```
score = domain_sim×0.25 + logo_sim×0.20 + dom_sim×0.15 + form×0.20 + ocr×0.10
      (normalize: ham_toplam / 0.90 — fingerprint_score Plus'ta gelecek)

≥0.70 → malicious | ≥0.40 → suspicious | else → clean
```

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
}
// MVP: PgmqJobQueue  |  İleride: KafkaJobQueue
```

### StorageService Interface

```scala
trait StorageService {
  def store(candidateId: Long, fileType: String, data: Array[Byte], contentType: String): Future[StorageRef]
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

1. ✅ PostgreSQL migration dosyaları (16 tablo + FK + index) — `app/migrations/drp-postgres/V001..V006`
2. ⬜ Repository / DAO katmanı (Slick)
3. ⬜ `JobQueue` interface + PGMQ implementasyonu
4. ⬜ `StorageService` interface + `PostgresBlobStorage`
5. ⬜ Demo seed datası (entity, asset, candidate)
6. ✅ Docker Compose (`ghcr.io/pgmq/pg18-pgmq:v1.10.0`) — `scripts/setup.ps1` / `setup.sh` ile tek komut kurulum
7. ✅ Env variable geçişi — `.env` + `.env.example`; `.gitignore`'a eklendi

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
| Actor/worker | Akka Actor | Crawler, analiz, risk scoring |
| DB | PostgreSQL + `slick-pg` | JSONB + pgmq için |
| Mesaj kuyruğu | PGMQ (pgmq extension) | `JobQueue` interface arkasında |
| Dosya depolama | PostgreSQL `bytea` / `blob_storage` | `StorageService` interface arkasında |
| Crawler | Browserless.io veya izole Docker/VM | Ana sunucudan izole |
| Domain similarity | Levenshtein, Jaro-Winkler | Scala veya Python |
| Logo/favicon similarity | pHash / imagehash | Hafif görsel benzerlik |
| OCR | Tesseract veya Google Vision API | |
| LLM özeti | OpenAI API veya muadili | MVP Plus; karar motoru değil |

---

## 10. Çalışma Kuralları

- **Önce keşfet → plan sun → onay al → uygula.** Büyük tek seferlik refactor yapma.
- **Her adımda `test/` altındaki ScalaTest suite'leri geçmeye devam etmeli.**
- **Modül parçalaması modül modül** — atomik, bağımsız commit'ler.
- **Migration stratejisi**: Play Evolutions/Flyway kullanılmaz; DRP PostgreSQL şeması manuel çalıştırılan `app/migrations/drp-postgres/V001..V006` dosyalarıyla yönetilir.
- **Todo baseline notu**: mevcut `app/migrations/initialize.sql` korunur; DRP migration seti ayrı PostgreSQL hedef şemasıdır.
- Kod içi yorum: WHY non-obvious olmadıkça ekleme.
- JSONB içine büyük içerik (HTML/DOM/screenshot) gömme → `blob_storage`.
- Pipeline worker'ları idempotent yaz.

---

## 11. Netleşen Kararlar

- **Migration aracı**: Play Evolutions/Flyway yok; manuel, versiyonlu SQL dosyaları kullanılacak.
- **Migration konumu**: DRP PostgreSQL dosyaları `app/migrations/drp-postgres/` altında tutulacak.
- **Şema sahipliği**: MVP'de tek PostgreSQL şeması ve açık FK'lar kullanılacak; modül sınırı kod paketleri ve port'lar üzerinden korunacak.

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
