# Scalability Düzeltmeleri — Mona DRP Veri Modeli

> MVP veri modelinde alınan beş ölçeklenebilirlik kararı. Hedef senaryo: **100.000 korunan domain × 2.000 permütasyon = 200M aday/gün.**

**Özet:** Bu revizyonda veri modelini komple yeniden yazmadık. Üç kritik noktayı düzelttik: ham candidate yükünü `candidate_discoveries` ile ayırdık, `outbox_jobs` yerine PGMQ tabanlı `JobQueue` kullandık, büyük dosyaları `blob_storage` ve `storage_ref` standardıyla ana tablolardan ayırdık. Son olarak JSONB alanlara boyut sınırı ve index'lere partial index ekledik.

Güncellenmiş model: orijinal 15 tablo → `candidate_discoveries` ve `blob_storage` eklendi, `outbox_jobs` PGMQ'ye taşındı = **16 tablo.**

---

## Karar 1 — candidate_discoveries: Candidate Öncesi Staging Tablosu

### Problem

200M permütasyonun tamamı doğrudan `candidates` tablosuna yazılıyordu. Bu iki baskı yaratıyor: günlük 200M INSERT ve her aday için sürekli status güncellemesi (`discovered → validating → crawling → ... → closed`). Daha önemlisi, permütasyonların büyük çoğunluğu DNS/HTTP kontrolünden geçemiyor — yani `candidates`'a hiç girmemesi gereken veriler oraya dolduruluyor. Bu gereksiz yük, her adaydan başlayan fan-out etkisini (crawl, sinyal, skor, kural) de şişiriyor. Ayrıca bugün inaktif olan bir domain yarın aktifleşirse bu yakalanmıyor.

### Çözüm

`candidates` tablosunun önüne **`candidate_discoveries`** adında hafif bir staging tablosu eklendi. Bu yeni bir analiz tablosu değil; yalnızca ön eleme katmanı.

```
Permütasyon üret (bellekte)
    → candidate_discoveries'a toplu yaz
    → Exclusion kontrolü → whitelisted? → dur
    → DNS/HTTP kontrol → inactive? → bekle, periyodik recheck
    → active → candidates'a terfi et
```

`candidate_discoveries`'da dikkat edilmesi gereken iki alan: `normalized_value` (aynı domainin farklı formatlarını tek kayıt sayar) ve `next_check_at` (inactive domainlerin yeniden kontrol zamanı; `check_count` arttıkça süre uzar — exponential backoff).

**Sonuç:** `candidates` artık sadece DNS/HTTP'den geçmiş aktif adayları tutuyor. Fan-out etkisi yalnızca gerçek adaylarda başlıyor. İnaktif domainler kaybolmuyor, `candidate_discoveries`'da takip ediliyor.

---

## Karar 2 — outbox_jobs Yerine PGMQ Tabanlı JobQueue

### Problem

`outbox_jobs` tablosu 200M–600M job/gün alacak ve her job için `pending → processing → done/failed` status geçişleriyle sürekli güncellenen bir hot table haline gelecekti. Bunun ötesinde: race condition (iki worker aynı job'ı alabilir), stuck job (işlenirken çöken worker), cleanup (done/failed job'lar tabloda birikir) gibi queue semantiğinin tamamını elle yazmak gerekiyordu.

### Çözüm

`outbox_jobs` veri modelinden çıkarıldı. Yerine PostgreSQL içinde çalışan **PGMQ** kullanılıyor. Uygulama kodu doğrudan PGMQ'ye değil, araya giren `JobQueue` interface'ine yazıyor.

```
Önceki: Uygulama kodu → outbox_jobs tablosu
Yeni:   Uygulama kodu → JobQueue interface → PGMQ
```

PGMQ'nun çözdüğü şeyler: **visibility timeout** (worker çökerse iş otomatik görünür olur, takılı kalmaz), **atomic read** (iki worker aynı job'ı alamaz), **cleanup** (tamamlanan job'lar otomatik gider). `JobQueue` interface sayesinde ileride Kafka'ya geçmek sadece implementasyon değişikliği — iş mantığına dokunulmuyor.

MVP'de pipeline, iş tiplerine göre ayrı queue'lara bölünür:

```
candidate_validation_queue  → DNS/HTTP kontrolü
crawl_queue                 → crawler işleri
feature_extraction_queue    → page feature çıkarımı
similarity_queue            → similarity analizi
risk_scoring_queue          → risk skorlama
```

Her mesaj yalnızca referans ID ve küçük parametreler taşır. HTML, DOM, OCR, screenshot kesinlikle payload'a girmez.

> **Not:** PGMQ kurulum yöntemi ortama göre değişir. Self-hosted / Docker'da `CREATE EXTENSION pgmq` yeterli. Managed servislerde (RDS, Cloud SQL) SQL-only kurulum kullanılır. Supabase, Aralık 2024'ten itibaren PGMQ'yu "Supabase Queues" olarak native destekliyor. Deployment öncesi ortamın durumu doğrulanmalıdır.

> **İdempotency:** PGMQ'da aynı mesaj teorik olarak iki kez işlenebilir. Worker'lar idempotent yazılmalı — aynı candidate iki kez promote edilmemeli, aynı crawl sonucu iki kez üretilmemeli.

---

## Karar 3 — Evidence Storage: blob_storage + storage_ref Standardı

### Problem

Crawler çalıştığında HTML arşivi, DOM snapshot, ekran görüntüsü, favicon, OCR çıktısı gibi büyük dosyalar üretilir. Bunların nereye gideceği tanımlanmamışsa geliştiriciler en kolay yere — JSONB alanlarına — gömer. Bu, `page_features`, `detection_signals`, `crawl_results` gibi tabloların hızla şişmesine ve geri dönmesi zor bir teknik borca yol açar. Veri modelinde `crawl_results.storage_ref` ve `evidence_files.storage_ref` kolonları zaten vardı ama bunların ne anlama geldiği tanımsızdı.

### Çözüm

Büyük binary ve text içerikler PostgreSQL içinde ayrı bir **`blob_storage`** tablosunda `bytea` olarak saklanır. Ana tablolar yalnızca bu tabloya işaret eden `storage_ref` referansını tutar.

```
crawl_results.storage_ref  →  blob_storage  (HTML, DOM snapshot, screenshot bundle)
evidence_files.storage_ref →  blob_storage  (tekil kanıt: OCR çıktısı, favicon, logo)
```

`storage_ref` formatı: `pg://evidence/{candidate_id}/{crawl_result_id}/{file_type}.{ext}`

İleride S3/MinIO'ya geçince format `s3://...` olur, `StorageService` interface'inin arkasındaki implementasyon değişir — veri modeli değişmez.

HTML, DOM, OCR gibi text içerikler uygulama tarafında gzip'lenerek saklanır (`compression = 'gzip'`). PNG/JPEG gibi zaten sıkıştırılmış dosyalar doğrudan `STORAGE EXTERNAL` ile tutulur — PostgreSQL tekrar sıkıştırmaya çalışmaz.

**Her tabloda JSONB ne tutar?**

| Alan | Ne tutar (doğru) | Ne gelmez |
|---|---|---|
| `page_features.dom_summary` | Yapısal özet: form/input/button sayıları | Tam DOM |
| `detection_signals.details` | Sinyal özeti, eşleşen text parçası | Ham OCR çıktısı |
| `crawl_results.metadata` | SSL, header, fetch süresi | Tam HTML |
| `risk_scores.llm_summary` | Özet açıklama metni | — |

Büyük içeriğe erişim zinciri: `page_features → crawl_result_id → crawl_results.storage_ref → blob_storage`. Her tabloya ayrı `storage_ref` kolonu eklemek gerekmez.

---

## Karar 4 — JSONB Alanları İçin CHECK Constraint'ler

### Problem

Karar 3 "büyük içerik blob_storage'a gider" dedi ama somut limit tanımlanmadı. Limit olmayınca geliştiriciler JSONB alanlarını şişiriyor — `dom_summary`'e tam DOM, `details`'a ham OCR, `llm_summary`'ye sınırsız LLM çıktısı giriyor.

### Çözüm

Her JSONB ve TEXT alanı için migration'a CHECK constraint eklendi:

```sql
-- page_features: yapısal özet, tam DOM değil (8 KB)
CHECK (dom_summary IS NULL OR pg_column_size(dom_summary) < 8192)

-- crawl_results: redirect zinciri slim (4 KB, max 30 hop)
CHECK (redirect_chain IS NULL OR pg_column_size(redirect_chain) < 4096)
CHECK (redirect_chain IS NULL OR jsonb_array_length(redirect_chain) <= 30)

-- detection_signals: sinyal özeti (4 KB)
CHECK (details IS NULL OR pg_column_size(details) < 4096)

-- risk_scores: LLM özeti (~750 kelime)
CHECK (llm_summary IS NULL OR char_length(llm_summary) < 5000)

-- candidates: sadece provenance bilgisi (2 KB)
CHECK (metadata IS NULL OR pg_column_size(metadata) < 2048)
```

`IS NULL OR` ifadesi hem NULL güvenliği sağlar hem de nullable alanların boş geçilmesine izin verir.

---

## Karar 5 — Index Düzeltmeleri

### Problem

Önerilen index'lerin bir kısmı gereksiz satırları da kapsıyor: `candidates(entity_id, status)` index'i `closed`/`eliminated` adayları da içeriyor ve her status değişiminde güncelleniyor. `evidence_files(content_hash)` index'i NULL değerleri kapsıyor — bunlar hiç sorgulanmıyor ama index'i şişiriyor.

### Çözüm

Her iki index'te de yalnızca sorgulanacak satırlar kapsama alındı:

```sql
-- candidates: artık yalnızca aktif pipeline adayları (IN ile — yeni terminal status
-- eklenmesi durumunda otomatik dışarıda kalır)
CREATE INDEX candidates_active_idx
  ON candidates(entity_id, created_at)
  WHERE status IN ('discovered', 'validating', 'crawling',
                   'analyzed', 'scored', 'review_needed');

-- evidence_files: NULL hash'li satırlar index dışında
CREATE INDEX evidence_files_content_hash_idx
  ON evidence_files(content_hash)
  WHERE content_hash IS NOT NULL;
```

`closed`, `eliminated`, `whitelisted` adaylar artık index'te yer kaplamıyor ve her status güncelleme işleminde index'i etkilemiyor. Yazma maliyeti ve index boyutu düşüyor.

---

## Özet Tablo

| Karar | Değişiklik | Etki |
|---|---|---|
| 1 — candidate_discoveries | Yeni staging tablosu | candidates yalnızca aktif adayları tutar |
| 2 — PGMQ | outbox_jobs kaldırıldı | Queue semantiği kutudan çıkıyor, Kafka geçişi kolay |
| 3 — blob_storage | Yeni storage tablosu + storage_ref standardı | Ana tablolar şişmez, S3'e geçiş veri modeli bozmaz |
| 4 — CHECK constraints | 5 alana boyut sınırı | JSONB disiplini migration seviyesinde garanti altında |
| 5 — Partial index | 2 index daraltıldı | Yazma maliyeti ve index boyutu düşer |

