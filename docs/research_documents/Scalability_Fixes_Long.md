# Scalability Düzeltmeleri — Mona DRP Veri Modeli

> Bu belge MVP veri modelinde alınan ölçeklenebilirlik kararlarını içerir. Her karar ayrı bir başlık altında sunulmuştur; ilerleyen fazlarda yeni kararlar buraya eklenecektir.

---

## Karar 1 — candidate_discoveries: Candidate Öncesi Staging Tablosu

### Problem

Mevcut modelde tüm permütasyonlar doğrudan `candidates` tablosuna yazılıyor. Hedef senaryo **100.000 korunan domain × 2.000 permütasyon = 200M aday/gün.** Bu iki ayrı baskı yaratıyor:

- **INSERT baskısı:** 200M satır günlük yazma
- **UPDATE baskısı:** Her aday `discovered → validating → crawling → analyzed → scored → closed` gibi sürekli status geçişleri yaşıyor

Oysa bu 200M permütasyonun büyük çoğunluğu DNS/HTTP kontrolünden geçemeyecek — yani `candidates` tablosuna hiç girmemesi gereken kayıtlar oraya dolduruluyor. `candidates`'tan başlayan fan-out etkisi (crawl_results, detection_signals, risk_scores, rule_results...) bu gereksiz satırlarla birlikte büyüyor.

Ayrıca bugün inaktif olan bir domain yarın aktifleşebilir. Mevcut modelde bu durumu takip etmek mümkün değil.

### Çözüm

`candidates` tablosunun önüne **`candidate_discoveries`** adında hafif bir staging tablosu ekliyoruz.

**Bu yeni bir analiz tablosu değil; candidate öncesi hafif bir eleme katmanı.**

Akış şöyle değişiyor:

```
Permütasyon üret (bellekte)
    → candidate_discoveries'a toplu yaz (batch insert)
    → Exclusion kontrolü → whitelistte mi?
          EVET → skip_reason = 'whitelisted', dur
          HAYIR → devam
    → DNS/HTTP kontrol et
          → dns_status ve http_status_code güncelle
          → check_count artır, next_check_at doldur
    → dns_status = 'active'?
          EVET → candidates'a insert et, candidate_id ve promoted_at doldur
          HAYIR → burada bekle
    → Periyodik job
          → dns_status = 'inactive' AND next_check_at <= now()
          → Tekrar kontrol et, aktifleşmişse candidates'a al
```

### Tablo Yapısı

```sql
CREATE TABLE candidate_discoveries (
  id                 BIGSERIAL PRIMARY KEY,

  entity_id          BIGINT NOT NULL REFERENCES entities(id),
  asset_id           BIGINT REFERENCES assets(id),
  -- NULL olabilir: ct_log / complaint gibi dış kaynaklarda asset belli olmayabilir

  value              TEXT NOT NULL,
  normalized_value   TEXT NOT NULL,
  -- Farklı formatları (https://Akbank.com, AKBANK.COM) aynı kayıt sayar

  source             TEXT NOT NULL DEFAULT 'permutation',
  -- permutation | ct_log | whois | manual_bulk | complaint

  dns_status         TEXT NOT NULL DEFAULT 'pending',
  -- pending | active | inactive | error

  http_status_code   INT,
  skip_reason        TEXT,
  -- whitelisted | duplicate | invalid_format | null

  check_count        INT NOT NULL DEFAULT 0,
  last_checked_at    TIMESTAMPTZ,
  next_check_at      TIMESTAMPTZ,
  -- check_count arttıkça süre uzatılabilir (exponential backoff)

  candidate_id       BIGINT REFERENCES candidates(id),
  promoted_at        TIMESTAMPTZ,

  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Index'ler

```sql
-- Aynı entity + normalize edilmiş domain tekrar girmesin
CREATE UNIQUE INDEX cd_entity_normalized_value_idx
  ON candidate_discoveries(entity_id, normalized_value);

-- DNS kontrol kuyruğu: bekleyenler, skip edilmeyenler
CREATE INDEX cd_pending_idx
  ON candidate_discoveries(entity_id, created_at)
  WHERE dns_status = 'pending' AND skip_reason IS NULL;

-- Periyodik recheck kuyruğu: inaktifler, zamanı gelenler
CREATE INDEX cd_recheck_idx
  ON candidate_discoveries(next_check_at)
  WHERE dns_status = 'inactive' AND skip_reason IS NULL;

-- Hangi candidate'a terfi ettiğini bulmak için
CREATE INDEX cd_candidate_id_idx
  ON candidate_discoveries(candidate_id)
  WHERE candidate_id IS NOT NULL;

-- Asset bazlı sorgular için
CREATE INDEX cd_asset_id_idx
  ON candidate_discoveries(asset_id)
  WHERE asset_id IS NOT NULL;
```

### Bu Tabloda Kasıtlı Olarak Olmayan Şeyler

| Olmayan | Neden |
|---|---|
| JSONB alan | Tablo hafif kalmalı, ham veri buraya gelmez |
| HTML / DOM / screenshot | Evidence pipeline'ı candidates sonrasında başlar |
| Status geçiş geçmişi | Sadece güncel dns_status yeterli |
| Ağır foreign key cascade | Silme işlemleri bu tabloyu etkilememeli |

### Bu Kararın Sağladığı Faydalar

1. `candidates` tablosu sadece gerçek analiz pipeline'ına girecek adayları tutar.
2. Candidate'dan başlayan fan-out etkisi (crawl → sinyal → skor → kural) azalır.
3. Exclusion ve DNS/HTTP kontrolü candidate öncesine alınır — gereksiz yük oluşmaz.
4. İnaktif domainler kaybolmaz; `next_check_at` ile periyodik tekrar kontrol edilir.
5. `check_count` ile exponential backoff uygulanabilir — defalarca inactive gelen domainler daha seyrek kontrol edilir.
6. Mevcut MVP veri modeli ana analiz tabloları açısından bozulmaz; `candidate_discoveries` yalnızca pipeline öncesi staging katmanı olarak eklenir. Karar 2 ile `outbox_jobs` custom tablosu kaldırılacağı için toplam tablo sayısı korunabilir.

---

## Karar 2 — outbox_jobs Yerine PGMQ Tabanlı JobQueue Kullanımı

### Problem

Mevcut veri modelinde `outbox_jobs` tablosu basit bir iş kuyruğu olarak tasarlanmıştı. Ancak 200M aday/gün seviyesinde her aday için DNS kontrolü, crawl, analiz ve risk scoring gibi işler üretildiğinde bu tablo **200M–600M job/gün** alacak ve sürekli `pending → processing → done/failed` status geçişleriyle hot table haline gelecek.

Custom `outbox_jobs` tablosu kullanılırsa şu problemleri bizim çözmemiz gerekir:

- Aynı job'ın birden fazla worker tarafından alınmaması
- Processing'de takılı kalan job'ların kurtarılması
- Retry ve backoff mantığı
- Done/failed job'ların tabloda sonsuza birikmemesi
- Payload'a büyük HTML, DOM, OCR, screenshot verisi konmaması
- İleride Kafka'ya geçişte büyük refactor yapılmaması

Yani problem sadece tablo büyümesi değil; queue semantiğini baştan sona bizim elle yönetmek zorunda kalmamız.

### Çözüm

`outbox_jobs` tablosu veri modelinden çıkarılır. Yerine PostgreSQL içinde çalışan **PGMQ** kullanılır. Ancak uygulama kodu doğrudan PGMQ'ye bağlanmaz — araya bir **`JobQueue` interface** girer.

```
Önceki yapı:
  Uygulama kodu → outbox_jobs tablosu

Yeni yapı:
  Uygulama kodu → JobQueue interface → PGMQ implementasyonu
```

Migration henüz yazılmadığı için bu kararın veri modeli seviyesindeki geçiş maliyeti düşüktür. `outbox_jobs` tablosunu hiç eklemeden direkt PGMQ üzerine kurulur.

### Hangi Problemleri Çözüyor?

**Visibility timeout:** Worker mesajı okuduğunda mesaj belirli süre diğer worker'lara görünmez olur. Worker çökerse visibility timeout süresi dolunca mesaj tekrar görünür hale gelir. Bu sayede takılı job riski azalır; ancak worker işlemleri yine idempotent yazılmalıdır.

**Race condition:** PGMQ atomic read sağlar. İki worker aynı job'ı aynı anda alamaz. outbox_jobs'ta bunu `SELECT FOR UPDATE SKIP LOCKED` ile elle yazmak gerekiyordu.

**Cleanup:** Başarılı job'lar `delete()` ile gider, audit gerekiyorsa `archive()` ile arşive alınır. Ana kuyruk şişmez.

**Kafka geçişi:** `JobQueue` interface sayesinde ileride aynı interface'in Kafka implementasyonu yazılır, iş mantığı hiç değişmez.

### Nasıl Kullanılır?

MVP'de iş tiplerine göre ayrı queue'lar tanımlanır:

```text
candidate_validation_queue   → DNS/HTTP kontrolü
crawl_queue                  → crawler işleri
feature_extraction_queue     → page feature çıkarımı
similarity_queue             → domain/logo/favicon/DOM similarity
risk_scoring_queue           → risk skoru hesaplama
```

Her mesaj küçük olmalı — sadece referans ID ve küçük parametreler:

```json
{ "job_type": "dns_check",  "target_type": "candidate_discovery", "target_id": 12345 }
{ "job_type": "crawl",      "target_type": "candidate",           "target_id": 891   }
{ "job_type": "similarity", "target_type": "crawl_result",        "target_id": 456   }
```

Payload'a kesinlikle şunlar girmez: HTML, DOM, OCR çıktısı, screenshot, binary dosya. Büyük içerik `storage_ref` ile ayrı saklama katmanında tutulur.

### JobQueue Interface

```scala
trait JobQueue {
  def enqueue(queue: String, payload: Json): Future[JobId]
  def dequeue(queue: String, count: Int, visibilityTimeout: Duration): Future[List[Job]]
  def complete(queue: String, jobId: JobId): Future[Unit]
  def fail(queue: String, jobId: JobId, error: String): Future[Unit]
  // PGMQ üzerinde retry/backoff, tekrar enqueue veya dead-letter queue mantığıyla implemente edilir
  def metrics(queue: String): Future[QueueMetrics]
}

// MVP implementasyonu
class PgmqJobQueue(pgmq: PgmqClient) extends JobQueue { ... }

// İleride — iş mantığı değişmez
class KafkaJobQueue(producer: KafkaProducer) extends JobQueue { ... }
```

`metrics()` metodu önemli: PGMQ kendi şemasında çalıştığı için "kaç job bekliyor?" gibi soruları bu interface üzerinden sorabilmek izlenebilirliği korur.

### Dikkat Edilmesi Gereken Üç Kural

**1. JobQueue interface zorunlu.**
Kod doğrudan PGMQ fonksiyonlarına bağlanmamalı. Yarın Kafka'ya geçildiğinde iş mantığına dokunulmamalı.

**2. Worker işlemleri idempotent yazılmalı.**
PGMQ'de aynı mesaj teorik olarak iki kez işlenebilir. Aynı candidate iki kez promote edilmemeli, aynı crawl sonucu iki kez üretilmemeli.

**3. Ortama göre doğru kurulum yöntemi seçilmeli.**

> **Not:** PGMQ kurulum yöntemi deployment ortamına göre değişebilir; managed servislerde extension veya SQL-only destek durumu deployment öncesi doğrulanmalıdır. Aşağıdaki tablo araştırma tarihindeki durumu yansıtır.

PGMQ'nun iki kurulum yöntemi var ve seçim ortama bağlı:

| Ortam | Yöntem | Durum |
|---|---|---|
| Self-hosted / Docker | Extension kurulumu | `CREATE EXTENSION pgmq` — tercih edilen yol |
| Supabase | Native destekli | "Supabase Queues" olarak dashboard'dan aktif edilebilir (Aralık 2024'ten itibaren) |
| AWS RDS (14.5+) | SQL-only veya pg_tle | SQL-only yöntemi önerilir |
| Google Cloud SQL | SQL-only kurulumu | Native listede yok, SQL-only çalışır |
| DigitalOcean Managed DB | SQL-only kurulumu | Native desteklenmiyor (Ağustos 2025 itibarıyla) |

**SQL-only kurulumu** managed servislerde şöyle yapılır:
```bash
psql -f pgmq-extension/sql/pgmq.sql postgres://user:pass@managed-db-host:5432/database
```
Bu komut `pgmq` şemasını ve tüm fonksiyonları doğrudan veritabanına yükler. Extension olarak görünmez ama tüm özellikler aynı şekilde çalışır.

> **Not:** MVP'de self-hosted Docker kullanılacaksa (`ghcr.io/pgmq/pg18-pgmq`) hiçbir ek adım gerekmez — pgmq kurulu gelir. İleride managed servis tercih edilirse tablodaki yönteme bakılmalıdır.

> **`fail()` hakkında not:** PGMQ'de mesajın "failed" olarak işaretlenmesi outbox_jobs'taki gibi otomatik değildir. `fail()` metodu uygulama seviyesinde retry/backoff, tekrar enqueue veya dead-letter queue mantığıyla implemente edilir. PGMQ her şeyi sihirli çözmüyor — interface bu davranışı soyutluyor.

### Sonuç

PGMQ, `outbox_jobs`'un yaratacağı status update baskısı, lock yönetimi, retry, cleanup ve tablo şişmesi problemlerini çözer. PostgreSQL içinde kalır, Kafka gibi ayrı altyapı gerektirmez. `JobQueue` interface sayesinde ileride Kafka'ya geçiş iş mantığına dokunulmadan yapılabilir.

**Hocaya sunum cümlesi:** *"outbox_jobs tablosunu hiç eklemedik. Migration henüz yazılmadığı için direkt PGMQ üzerine JobQueue implementasyonunu kurduk. İş mantığı interface'e bağlı, ileride Kafka'ya geçmek istediğimizde sadece implementasyon değişiyor."*

---

## Karar 3 — Evidence Storage Strategy: blob_storage + storage_ref Standardı

### Problem

Crawler bir şüpheli siteyi gezdiğinde HTML arşivi, DOM snapshot, ekran görüntüsü, favicon ve OCR çıktısı gibi büyük içerikler üretir. Bu içeriklerin nerede ve nasıl saklanacağı tanımlanmazsa geliştiriciler bunları en kolay yere — ana tablolardaki JSONB alanlarına — gömer. Bu da tablolar şişer ve geri dönmesi zor bir teknik borç bırakır.

**Yanlış olan şu:**
```
page_features.dom_summary     → full DOM yapısı gömülür
detection_signals.details     → tüm OCR çıktısı gömülür
crawl_results.metadata        → full HTML gömülür
evidence_files                → screenshot binary'si direkt konur
```

**Neden sorun?** Her aday için bu veri üretildiğinde `page_features`, `detection_signals`, `crawl_results` gibi tablolar hızla gigabyte'lara ulaşır. JSONB alanları büyüdükçe sorgular yavaşlar, yedekler şişer ve ileride S3/MinIO'ya taşımak için bu alanları teker teker ayıklamak gerekir.

Veri modelinde `crawl_results.storage_ref` ve `evidence_files.storage_ref` kolonları zaten var; ancak bu `storage_ref`'in ne anlama geldiği, hangi formatta olduğu ve arkasında neyin çalıştığı tanımlanmamış. Bu karar onu tanımlar.

### Çözüm

MVP kapsamında S3/MinIO kurulmayacağı için büyük binary ve text içerikler PostgreSQL içinde ayrı bir **`blob_storage`** tablosunda `bytea` olarak saklanır. Ana operasyonel tablolar yalnızca bu tabloya işaret eden `storage_ref` değerini tutar.

```
Ana tablo                      blob_storage tablosu
─────────────────────          ──────────────────────────────────
evidence_files.storage_ref  →  blob_storage.storage_ref
crawl_results.storage_ref   →  blob_storage.storage_ref

JSONB alanlarda yalnızca:       blob_storage.data'da:
  - Kısa özetler                - Gerçek HTML/DOM/screenshot
  - Küçük sinyal değerleri      - OCR çıktısı
  - Metadata                    - Favicon binary
```

Kod hiçbir zaman `blob_storage` tablosuna doğrudan bağlanmaz. Erişim bir **`StorageService`** interface üzerinden yapılır; böylece ileride tek bir implementasyon değişikliğiyle MinIO/S3'e geçilir.

### Tablo Yapısı

```sql
CREATE TABLE blob_storage (
  id               BIGSERIAL PRIMARY KEY,

  storage_ref      TEXT NOT NULL UNIQUE,
  -- Erişim anahtarı. Format: pg://evidence/{candidate_id}/{file_type}.{ext}
  -- Geçişte: s3://mona-drp/evidence/{candidate_id}/{file_type}.{ext}

  file_type        TEXT NOT NULL,
  -- html_archive | screenshot | dom_snapshot | ocr_output | favicon | logo

  content_type     TEXT NOT NULL,
  -- text/html | image/png | image/jpeg | application/json | text/plain

  data             BYTEA NOT NULL,

  size_bytes       BIGINT NOT NULL,

  content_hash     TEXT,
  -- SHA-256 hash — recurrence detection ve dedup için
  -- evidence_files.content_hash ile aynı değer

  compression      TEXT NOT NULL DEFAULT 'none',
  -- none | gzip
  -- HTML/DOM/OCR → uygulama tarafında gzip'lenerek saklanır
  -- PNG/JPEG → zaten sıkıştırılmış, tekrar sıkıştırılmaz

  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- PNG/JPEG gibi zaten sıkıştırılmış dosyalar için:
-- PostgreSQL'in boşuna tekrar sıkıştırma denemesini engeller
ALTER TABLE blob_storage ALTER COLUMN data SET STORAGE EXTERNAL;

-- Recurrence detection ve dedup sorguları için
CREATE INDEX blob_storage_hash_idx
  ON blob_storage(content_hash)
  WHERE content_hash IS NOT NULL;

-- Cleanup, retention ve migration sorguları için
CREATE INDEX blob_storage_created_at_idx
  ON blob_storage(created_at);
```

### storage_ref Formatı

`storage_ref` string değeri, backend ne olursa olsun aynı kalır. Prefix bakılarak hangi implementasyonun kullanıldığı anlaşılır:

```
pg://evidence/42/screenshot.png          → MVP: blob_storage tablosu
pg://evidence/42/html_archive.html.gz    → MVP: sıkıştırılmış HTML
pg://evidence/42/dom_snapshot.json.gz    → MVP: sıkıştırılmış DOM

s3://mona-drp/evidence/42/screenshot.png → İleride: S3/MinIO
```

### STORAGE EXTERNAL ve Sıkıştırma Stratejisi

`SET STORAGE EXTERNAL`, PostgreSQL'in veriyi TOAST tabloya taşımasına ama tekrar sıkıştırmamasına neden olur. İki farklı içerik türü için farklı strateji:

- **PNG, JPEG, favicon** gibi zaten sıkıştırılmış dosyalar: `STORAGE EXTERNAL` ile doğrudan saklanır. PostgreSQL'in compression denemesi hem CPU israfı hem de veri büyümesine neden olur.
- **HTML, DOM, OCR çıktısı** gibi text içerikler: Uygulama tarafında `gzip` ile sıkıştırılıp `blob_storage.data`'ya yazılır. `compression = 'gzip'` kolonu, okurken decompress yapılacağını belirtir.

### StorageService Interface

```scala
trait StorageService {
  def store(
    candidateId: Long,
    fileType:    String,
    data:        Array[Byte],
    contentType: String
  ): Future[StorageRef]

  def retrieve(ref: StorageRef): Future[Array[Byte]]
  def delete(ref: StorageRef): Future[Unit]
  def exists(ref: StorageRef): Future[Boolean]
}

// MVP implementasyonu — blob_storage tablosuna yazar
class PostgresBlobStorage(db: Database) extends StorageService { ... }

// İleride — interface değişmez, veri modeli değişmez
class MinioStorage(client: MinioClient) extends StorageService { ... }
class S3Storage(client: S3Client) extends StorageService { ... }
```

### Mevcut Veri Modeliyle Entegrasyon

Güncellenmiş MVP veri modelinde `candidate_discoveries` ve `blob_storage` eklenmiş, `outbox_jobs` ise PGMQ'ye taşınmıştır. Bu yeni yapıda `crawl_results.storage_ref` ve `evidence_files.storage_ref` kolonları `blob_storage.storage_ref` standardına göre somutlaştırılır:

| Alan | Ne tutar | Neye işaret eder |
|---|---|---|
| `crawl_results.storage_ref` | HTML/DOM/screenshot bundle referansı | `blob_storage.storage_ref` |
| `evidence_files.storage_ref` | Tekil kanıt dosyası referansı | `blob_storage.storage_ref` |
| `evidence_files.content_hash` | Dosya parmak izi | `blob_storage.content_hash` ile aynı |
| `page_features.dom_summary` | Yalnızca kısa yapısal özet | — |
| `detection_signals.details` | Yalnızca sinyal özeti ve skor | — |
| `crawl_results.metadata` | SSL, header, fetch süresi gibi slim veri | — |

### Neden pg_largeobject Değil?

Bu kararın alternatifi pg_largeobject'ti. Reddedilme nedenleri:

- Dosyalarımız (HTML ~500KB, screenshot ~2MB) 1GB sınırının çok altında — pg_largeobject'in tek avantajı olan 4TB sınırı bizim için anlamsız
- Orphan riski: satır silindiğinde Large Object otomatik silinmiyor, trigger + periyodik `vacuumlo` şart
- Benchmark'larda `bytea` + EXTERNAL storage, Large Object'ten daha hızlı streaming sunuyor
- Özel LO API gerektiriyor — normal SQL ile çalışmıyor, transaction zorunlu

### Bu Kararın Sağladığı Faydalar

1. Ana tablolar (`page_features`, `detection_signals`, `crawl_results`) şişmez; yalnızca referans tutar.
2. `evidence_files` gerçek bir metadata tablosu olarak kalır, binary içerik taşımaz.
3. `StorageService` interface sayesinde MinIO/S3'e geçiş veri modelini bozmadan yapılır.
4. `compression` kolonu sayesinde text içerikler gzip'li, binary içerikler ham olarak doğru şekilde yönetilir.
5. `content_hash` hem `blob_storage` hem `evidence_files`'ta tutulduğundan recurrence detection ve dedup doğrudan desteklenir.

**Hocaya sunum cümlesi:** *"HTML, DOM, screenshot gibi büyük içerikleri ana analiz tablolarına gömmek yerine ayrı bir blob_storage tablosunda tutuyoruz. Ana tablolar yalnızca storage_ref ile işaret ediyor. Erişim StorageService interface üzerinden, ileride MinIO ya da S3'e geçince sadece implementasyon değişiyor, veri modeline hiç dokunmuyoruz."*

---

## Karar 4 — JSONB Alanları İçin CHECK Constraint'ler

### Problem

Veri modelindeki JSONB ve TEXT alanları için somut boyut limiti tanımlanmamış. Karar 3 "büyük içerik blob_storage'a gider" kararını aldı; ancak "büyük" ne demek, hangi alanda ne kadar veri tutulabilir sorusu cevapsız kaldı. Limit olmayınca geliştiriciler içgüdüsel olarak JSONB alanlarını şişiriyor:

- `page_features.dom_summary` → full DOM yapısı buraya gömülüyor
- `detection_signals.details` → ham OCR çıktısı buraya dolabiliyor
- `crawl_results.redirect_chain` → uzun redirect zincirleri JSONB'ye giriyor
- `risk_scores.llm_summary` → sınırsız LLM çıktısı TEXT'e yazılıyor
- `candidates.metadata` → ham kaynak çıktısı taşınabiliyor

### Çözüm: Her Alan Neyi Tutacak?

Her JSONB alanının "büyük içerik deposu" değil "küçük özet" olması gerekiyor. Büyük içerik için erişim zinciri zaten mevcut:

```
page_features     → crawl_result_id → crawl_results.storage_ref → blob_storage (tam DOM)
detection_signals → crawl_result_id → crawl_results.storage_ref → blob_storage (tam HTML)
evidence_files    → storage_ref     → blob_storage               (OCR çıktısı, screenshot)
```

Yeni `storage_ref` kolonları eklemeye gerek yok — büyük içerik bu zincir üzerinden zaten erişilebilir. Yapılması gereken: küçük özet alanların CHECK constraint ile disipline alınması.

### Alan Bazlı Kurallar ve Constraint'ler

**`page_features.dom_summary`** — Yapısal özet, tam DOM değil. Kaç form, kaç input, kaç button, başlık dağılımı gibi küçük JSON. Tam DOM zaten `crawl_results.storage_ref`'te duruyor.

```sql
ALTER TABLE page_features
  ADD CONSTRAINT pf_dom_summary_size
  CHECK (dom_summary IS NULL OR pg_column_size(dom_summary) < 8192);
-- 8 KB üstü → dom_summary yanlış kullanılıyor demektir
```

**`crawl_results.redirect_chain`** — Redirect URL'leri, max 20-30 hop. Ham veri JSONB'ye gömülmez. Hem byte boyutu hem mantıksal hop sayısı sınırlandırılır.

```sql
ALTER TABLE crawl_results
  ADD CONSTRAINT cr_redirect_chain_size
  CHECK (redirect_chain IS NULL OR pg_column_size(redirect_chain) < 4096);
-- 4 KB yeterli; fazlası blob_storage'a storage_ref ile referanslanır

ALTER TABLE crawl_results
  ADD CONSTRAINT cr_redirect_chain_hop_limit
  CHECK (
    redirect_chain IS NULL
    OR jsonb_typeof(redirect_chain) <> 'array'
    OR jsonb_array_length(redirect_chain) <= 30
  );
-- Mantıksal limit: maksimum 30 hop
```

**`detection_signals.details`** — Sinyal özeti ve skoru. Ham OCR çıktısı veya full HTML buraya gelmez; eşleşen text parçası, score, hangi alanda bulunduğu gibi küçük bilgi.

```sql
ALTER TABLE detection_signals
  ADD CONSTRAINT ds_details_size
  CHECK (details IS NULL OR pg_column_size(details) < 4096);
-- Ham OCR → evidence_files.storage_ref → blob_storage (file_type = 'ocr_output')
```

**`risk_scores.llm_summary`** — Özet açıklama metni. Sınırsız büyüyemez.

```sql
ALTER TABLE risk_scores
  ADD CONSTRAINT rs_llm_summary_length
  CHECK (llm_summary IS NULL OR char_length(llm_summary) < 5000);
-- ~750 kelime; özet için fazlasıyla yeterli
```

**`candidates.metadata`** — Sadece provenance: complaint_id, ct_cert_id, hangi domain'den türedi. Doğası gereği küçük.

```sql
ALTER TABLE candidates
  ADD CONSTRAINT cand_metadata_size
  CHECK (metadata IS NULL OR pg_column_size(metadata) < 2048);
```

### Neden Yeni storage_ref Kolonu Eklemiyoruz?

Büyük içeriğe erişim için her tabloya ayrı `storage_ref` eklemek gerekmez. `crawl_results.storage_ref` ve `evidence_files.storage_ref` zaten tüm pipeline'ı kapsıyor:

| Büyük içerik | Nerede duruyor | Nasıl erişilir |
|---|---|---|
| Tam DOM | `blob_storage` | `page_features → crawl_result_id → crawl_results.storage_ref` |
| Ham OCR çıktısı | `blob_storage` | `evidence_files.storage_ref` (file_type = 'ocr_output') |
| Full HTML/screenshot | `blob_storage` | `crawl_results.storage_ref` |
| Logo/favicon binary | `blob_storage` | `evidence_files.storage_ref` (file_type = 'favicon' \| 'logo') |

Mevcut iki `storage_ref` kolonu (`crawl_results`, `evidence_files`) tüm ihtiyacı karşılıyor. Yapı tutarlı, yeni kolon eklemeye gerek yok.

**Hocaya sunum cümlesi:** *"Her JSONB alanının ne tutacağını CHECK constraint ile garanti altına aldık. Büyük içerik için yeni storage_ref kolonları eklemedik — crawl_results ve evidence_files üzerindeki mevcut storage_ref zinciri tüm tablolara erişim sağlıyor."*

---

## Karar 5 — Index Düzeltmeleri

### Problem

Veri modelinde önerilen index'lerin bir kısmı büyük hacimde şişecek şekilde tanımlanmış. Belge "aktif status'lar için daraltılmış indeks düşünülmeli" ve "content_hash IS NOT NULL için daraltılmış indeks" diyor; ancak bunlar henüz migration'a yansımamış.

İki sorun var:

**candidates(entity_id, status):** Status sürekli değiştiği için (`discovered → validating → crawling → … → closed`) bu index her güncelleme işleminde yazılıyor. `closed`, `eliminated`, `whitelisted` adaylar artık hiç sorgulanmıyor ama index'te yer kaplamaya ve güncelleme maliyeti yaratmaya devam ediyor.

**evidence_files(content_hash):** NULL değerler index'e giriyor. Birçok `evidence_files` satırı `content_hash = NULL` ile başlayabilir. NULL satırlar bu index üzerinden hiçbir zaman sorgulanmıyor ama index'i şişiriyor.

### Çözüm

**candidates index — daraltılmış partial index:**

```sql
-- Mevcut index'i kaldır
DROP INDEX IF EXISTS candidates_entity_status_idx;

-- Sadece aktif pipeline adayları için (IN ile — yeni terminal status eklenmesi durumunda güvenli)
CREATE INDEX candidates_active_idx
  ON candidates(entity_id, created_at)
  WHERE status IN ('discovered', 'validating', 'crawling',
                   'analyzed', 'scored', 'review_needed');

-- Status bazlı pipeline sorguları için
CREATE INDEX candidates_status_pipeline_idx
  ON candidates(status, updated_at)
  WHERE status IN ('discovered', 'validating', 'crawling',
                   'analyzed', 'scored', 'review_needed');
```

**evidence_files index — NULL'ları dışarıda bırak:**

```sql
-- Mevcut index'i kaldır
DROP INDEX IF EXISTS evidence_files_content_hash_idx;

-- Sadece hash'i olan satırlar için
CREATE INDEX evidence_files_content_hash_idx
  ON evidence_files(content_hash)
  WHERE content_hash IS NOT NULL;
```

### Bu Düzeltmelerin Sağladığı Faydalar

**candidates partial index:**
- `closed`/`eliminated`/`whitelisted` adaylar index'e girmez → index boyutu küçük kalır
- Her status güncelleme işleminde `closed` adaylar index'i etkilemez → yazma maliyeti düşer
- Pipeline sorguları sadece aktif adayları tarar → sorgu hızlanır

**evidence_files partial index:**
- NULL hash'li satırlar index'ten çıkar → gereksiz index büyümesi engellenir
- Recurrence detection sorguları (`WHERE content_hash = ?`) sadece hash'i olan satırları tarar

**Hocaya sunum cümlesi:** *"candidates index'ini aktif pipeline adaylarıyla sınırladık — closed ve eliminated adaylar artık index'e girmiyor. evidence_files'ta da content_hash NULL olan satırları index dışında bıraktık. İkisi de yazma maliyetini ve index boyutunu küçültüyor."*

---

*Sonraki karar buraya eklenecek.*
