# Mona DRP v5 — Migration Öncesi Final Şema Kararları

Bu doküman, Mona DRP v5 veri modelini PostgreSQL migration dosyasına çevirmeden önce netleştirilmiş son şema kararlarını içerir.

Amaç, veri modelinde açıklama seviyesinde kalan alanları migration yazılabilir hale getirmektir:

* PostgreSQL kolon tipi
* Nullable / NOT NULL kararı
* Default değer
* Foreign key ilişkileri
* ON DELETE davranışları
* Unique constraint
* CHECK constraint
* Index kararları
* updated_at güncelleme stratejisi
* Final migration yorumları

Bu doküman doğrudan SQL migration değildir; `app/migrations/drp-postgres/` altındaki manuel PostgreSQL migration seti yazılırken referans alınacak final şema çıktısıdır.

Güncel migration parçalama kararı:

```text
V001__asset_layer_up/down.sql
V002__discovery_layer_up/down.sql
V003__storage_layer_up/down.sql
V004__pipeline_layer_up/down.sql
V005__decision_layer_up/down.sql
V006__pgmq_queues_up/down.sql
```

---

# 1. Genel Kararlar

Mona DRP MVP Core veri modeli PostgreSQL üzerinde kurulacaktır.

Toplam tablo sayısı: 16

```text
1. entities
2. asset_groups
3. assets
4. exclusions
5. candidate_discoveries
6. candidates
7. blob_storage
8. crawl_results
9. page_features
10. candidate_asset_matches
11. detection_signals
12. risk_scores
13. rule_results
14. reviews
15. cases
16. evidence_files
```

`outbox_jobs` tablosu oluşturulmayacaktır. İş kuyruğu mantığı PGMQ + `JobQueue` interface üzerinden yönetilecektir.

PGMQ queue mesajları yalnızca küçük referanslar taşıyacaktır:

```text
target_type
target_id
job_type
small params
```

HTML, DOM, OCR çıktısı, screenshot veya binary dosya queue payload içine konmayacaktır.

---

# 2. Yeni Netleştirilen Final Kararlar

## 2.1 ON DELETE Kararı

Tüm foreign key ilişkilerinde varsayılan davranış açıkça tanımlanacaktır:

```sql
ON DELETE RESTRICT
```

Yani MVP’de cascade delete yoktur.

Gerekçe:

* DRP verisi evidence/provenance/verdict içerir.
* Candidate, crawl result, risk score, review, case ve evidence kayıtları geçmiş izidir.
* Yanlışlıkla bir üst kayıt silindiğinde alt kanıtların kaybolması kabul edilmez.
* Fiziksel silme yerine kapatma, pasifleştirme veya status değişimi tercih edilir.

Bu yüzden:

```text
entities silinirse asset/candidate zinciri silinmez; silme engellenir.
candidates silinirse crawl/risk/review/evidence zinciri silinmez; silme engellenir.
risk_scores silinirse rule_results silinmez; silme engellenir.
reviews silinirse cases silinmez; silme engellenir.
```

Down migration ayrı konudur; tablolar ters FK sırasıyla düşürülecektir.

---

## 2.2 `candidates.status` İçinden `whitelisted` Çıkarıldı

`whitelisted` normal candidate status listesinde yer almayacaktır.

Final candidate status listesi:

```text
validated
crawled
analyzed
scored
reviewed
closed
eliminated
error
```

Gerekçe:

Whitelist mantığı normalde candidate oluşmadan önce, staging aşamasında uygulanır:

```text
candidate_discoveries.skip_reason = 'whitelisted'
```

Yani aktif exclusion’a takılan bir kayıt `candidates` tablosuna promote edilmez.

Candidate’a kadar gelmiş bir kayıt sonradan masum bulunursa bu durum `whitelisted` status ile değil, human review kararıyla temsil edilir:

```text
reviews.decision = 'false_positive'
```

veya pipeline’dan çıkarılacaksa:

```text
candidates.status = 'eliminated'
```

Bu sayede whitelist, false positive ve pipeline elimination birbirine karışmaz.

---

## 2.3 `candidates.discovery_id NOT NULL`

Final karar:

```sql
discovery_id BIGINT NOT NULL
```

Bunun anlamı:

```text
Manual dahil bütün inputlar önce candidate_discoveries staging tablosuna girer.
Hiçbir aday doğrudan candidates tablosuna yazılmaz.
```

Bu karar bilinçlidir.

Gerekçe:

* Exclusion kontrolü tek noktada yapılır.
* DNS/HTTP validation tek noktada yapılır.
* Duplicate guard `candidate_discoveries(entity_id, normalized_value)` üzerinden başlar.
* Promote ilişkisi `candidates.discovery_id` üzerinden izlenir.
* Candidate tablosu ham keşif havuzu değil, yalnızca analiz pipeline’ına alınan gerçek adayları tutar.

---

## 2.4 Evidence Package / Case Sabitleme Kararı

MVP’de `evidence_files` içinde `case_id` olmayacaktır.

Kanıt paketi şu şekilde türetilecektir:

```text
case → candidate_id → evidence_files
```

Yani case’e ait evidence listesi, aynı candidate’a bağlı evidence kayıtlarından okunur.

Final MVP kabulü:

```text
MVP’de case-specific frozen evidence set yoktur.
Evidence package candidate üzerinden türetilir.
Candidate closed olduktan sonra aynı candidate için yeni evidence yazılmaması application service tarafından korunur.
```

Bu, MVP için kabul edilen bilinçli sadeleştirmedir.

İleride case anındaki evidence setini tamamen sabitlemek gerekirse yeni bir join tablo eklenir:

```text
case_evidence
```

Örnek:

```text
case_id
evidence_file_id
included_at
```

Bu ekleme mevcut şemayı kırmadan yapılabilir.

---

## 2.5 Enum-like TEXT Alanlarda CHECK Kararı

PostgreSQL enum kullanılmayacaktır.

Ancak kritik lifecycle alanlarında `TEXT + CHECK` kullanılacaktır.

CHECK kullanılacak alanlar:

```text
candidate_discoveries.dns_status
candidate_discoveries.skip_reason
candidates.status
risk_scores.verdict
reviews.decision
cases.status
cases.priority
blob_storage.compression
assets.asset_type
evidence_files.file_type
blob_storage.file_type
```

CHECK kullanılmayacak / daha esnek bırakılacak alanlar:

```text
candidate_discoveries.source
candidates.source
candidate_asset_matches.match_type
detection_signals.signal_type
rule_results.rule_code
entities.type
exclusions.reason
blob_storage.content_type
```

Gerekçe:

* Lifecycle alanlarında hatalı değer pipeline’ı bozar.
* Source, signal, match, rule gibi alanlar ileride genişleyebilir.
* PostgreSQL enum yerine TEXT kullanıldığı için ileride yeni değer eklemek daha kolaydır.
* Kritik alanlarda CHECK, typo ve yanlış status riskini azaltır.

---

## 2.6 `updated_at` Güncelleme Kararı

Mutable tablolarda `updated_at DEFAULT now()` yalnızca insert anını karşılar.

Update anında otomatik değişmesi için PostgreSQL trigger kullanılacaktır.

Final karar:

```text
Ortak `set_updated_at()` trigger function V001 asset layer migration içinde oluşturulacak.
Application servisleri ayrıca updated_at set etmek zorunda kalmayacak.
```

Trigger uygulanacak tablolar:

```text
entities
asset_groups
assets
exclusions
candidate_discoveries
candidates
cases
```

Örnek yaklaşım:

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Her mutable tabloya `BEFORE UPDATE` trigger bağlanacaktır.

---

## 2.7 Ek Unique / Index Kararları

Final şemaya ek güvenlik index’leri eklenecektir:

```text
asset_groups(entity_id, name) UNIQUE
assets(entity_id, asset_type, value) WHERE is_active = true UNIQUE
exclusions için aktif duplicate guard
cases için aynı candidate üzerinde tek aktif case
reviews(candidate_id, reviewed_at DESC)
```

Bu index’ler uygulama hatalarına karşı DB seviyesinde ikinci güvenlik katmanı sağlar.

---

# 3. Audit / Timestamp Konvansiyonu

## Mutable tablolar

Aşağıdaki tablolar current-state tablolardır. Bu yüzden hem `created_at` hem `updated_at` taşır:

```text
entities
asset_groups
assets
exclusions
candidate_discoveries
candidates
cases
```

Standart:

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT now()
updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

`updated_at`, PostgreSQL trigger ile update anında otomatik güncellenecektir.

## Immutable / append-only tablolar

Aşağıdaki tablolar pipeline çıktısıdır. Bu yüzden `updated_at` taşımaz:

```text
blob_storage
crawl_results
page_features
candidate_asset_matches
detection_signals
risk_scores
rule_results
reviews
evidence_files
```

Standart:

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

Domain zaman damgaları ayrıca tutulabilir:

```text
crawl_results.crawled_at
reviews.reviewed_at
evidence_files.captured_at
```

---

# 4. Enum / String Kararı

DB tarafında PostgreSQL enum kullanılmayacaktır.

Aşağıdaki alanlar `TEXT` olarak tutulacaktır:

```text
entities.type
assets.asset_type
candidate_discoveries.source
candidate_discoveries.dns_status
candidate_discoveries.skip_reason
candidates.source
candidates.status
candidate_asset_matches.match_type
detection_signals.signal_type
risk_scores.verdict
rule_results.rule_code
reviews.decision
cases.status
cases.priority
evidence_files.file_type
blob_storage.file_type
blob_storage.content_type
blob_storage.compression
```

Kritik lifecycle alanlarında `CHECK` constraint kullanılacaktır.

Kod tarafında sealed/open enum + codec kullanılacaktır.

---

# 5. Skor Tipi Kararı

Aşağıdaki skor / ağırlık alanlarında ortak tip kullanılacaktır:

```sql
NUMERIC(5,4)
```

Bu alanlar 0 ile 1 arasında değer taşıyacaktır:

```text
candidate_asset_matches.similarity_score
detection_signals.score
risk_scores.total_score
risk_scores.confidence
rule_results.weight
```

Final karar:

```text
NUMERIC(5,4) kullanılacak.
Yanına CHECK 0..1 constraint eklenecek.
```

Gerekçe:

`NUMERIC(3,2)` daha dar görünse de `0.8765`, `0.9123`, `0.6950` gibi threshold’a yakın değerleri yuvarlar. Risk scoring tarafında eşiklere yakın skorlar önemli olduğu için dört ondalık basamak korunacaktır.

---

# 6. Tablo Tablo Final Şema

---

## 6.1 `entities`

Korunan marka, kurum veya kişiyi temsil eder.

| Kolon      | Tip         | Nullable | Default | Constraint / Not                 |
| ---------- | ----------- | -------- | ------- | -------------------------------- |
| id         | BIGSERIAL   | NOT NULL | auto    | PRIMARY KEY                      |
| name       | TEXT        | NOT NULL | —       | Entity adı                       |
| type       | TEXT        | NOT NULL | —       | brand / person / institution / … |
| created_at | TIMESTAMPTZ | NOT NULL | now()   | —                                |
| updated_at | TIMESTAMPTZ | NOT NULL | now()   | Trigger ile güncellenir          |

Not:

`entities.type` için DB CHECK kullanılmayacaktır. Çünkü ileride `brand`, `person`, `institution` dışında yeni tipler eklenebilir.

---

## 6.2 `asset_groups`

Entity altında opsiyonel alt gruplama.

| Kolon      | Tip         | Nullable | Default | Constraint / Not                      |
| ---------- | ----------- | -------- | ------- | ------------------------------------- |
| id         | BIGSERIAL   | NOT NULL | auto    | PRIMARY KEY                           |
| entity_id  | BIGINT      | NOT NULL | —       | FK → entities(id), ON DELETE RESTRICT |
| name       | TEXT        | NOT NULL | —       | Grup adı                              |
| created_at | TIMESTAMPTZ | NOT NULL | now()   | —                                     |
| updated_at | TIMESTAMPTZ | NOT NULL | now()   | Trigger ile güncellenir               |

UNIQUE:

```sql
UNIQUE (entity_id, name)
```

MVP’de boş kalabilir. Ancak ileride alt marka/kampanya ayrımı için tablo korunur.

---

## 6.3 `assets`

Korunan dijital varlığı temsil eder.

| Kolon          | Tip         | Nullable | Default     | Constraint / Not                          |
| -------------- | ----------- | -------- | ----------- | ----------------------------------------- |
| id             | BIGSERIAL   | NOT NULL | auto        | PRIMARY KEY                               |
| entity_id      | BIGINT      | NOT NULL | —           | FK → entities(id), ON DELETE RESTRICT     |
| asset_group_id | BIGINT      | NULL     | —           | FK → asset_groups(id), ON DELETE RESTRICT |
| asset_type     | TEXT        | NOT NULL | —           | domain / subdomain                        |
| value          | TEXT        | NOT NULL | —           | Örn. akbank.com                           |
| metadata       | JSONB       | NOT NULL | '{}'::jsonb | Küçük referans metadata                   |
| is_active      | BOOLEAN     | NOT NULL | true        | Silme yerine pasifleştirme                |
| created_at     | TIMESTAMPTZ | NOT NULL | now()       | —                                         |
| updated_at     | TIMESTAMPTZ | NOT NULL | now()       | Trigger ile güncellenir                   |

`metadata` içine sadece küçük referanslar girer:

```text
homepage_url
login_page_url
logo_ref
favicon_ref
reference_dom_summary
```

Şunlar metadata içine girmez:

```text
binary logo
base64 favicon
full DOM
HTML archive
screenshot
uzun OCR çıktısı
```

CHECK:

```sql
asset_type IN ('domain', 'subdomain')
```

UNIQUE partial index:

```sql
UNIQUE (entity_id, asset_type, value)
WHERE is_active = true
```

---

## 6.4 `exclusions`

Allowlist / kontrol edilmeyecek domain veya URL kayıtlarıdır.

| Kolon      | Tip         | Nullable | Default  | Constraint / Not                                    |
| ---------- | ----------- | -------- | -------- | --------------------------------------------------- |
| id         | BIGSERIAL   | NOT NULL | auto     | PRIMARY KEY                                         |
| entity_id  | BIGINT      | NULL     | —        | FK → entities(id), ON DELETE RESTRICT               |
| value      | TEXT        | NOT NULL | —        | Dışlanacak domain/URL                               |
| match_type | TEXT        | NOT NULL | —        | exact / registrable_domain / subdomain_of / pattern |
| reason     | TEXT        | NOT NULL | —        | manual / owned_unmonitored / third_party_legit / …  |
| is_active  | BOOLEAN     | NOT NULL | true     | Aktif dışlama mı                                    |
| created_by | TEXT        | NOT NULL | 'system' | Auth gelene kadar default system                    |
| created_at | TIMESTAMPTZ | NOT NULL | now()    | —                                                   |
| updated_at | TIMESTAMPTZ | NOT NULL | now()    | Trigger ile güncellenir                             |

Final karar:

```text
created_by NOT NULL DEFAULT 'system'
```

CHECK:

```sql
match_type IN ('exact', 'registrable_domain', 'subdomain_of', 'pattern')
```

Aktif duplicate guard:

```sql
UNIQUE (entity_id, value, match_type)
WHERE is_active = true AND entity_id IS NOT NULL
```

Global exclusion duplicate guard:

```sql
UNIQUE (value, match_type)
WHERE is_active = true AND entity_id IS NULL
```

Not:

`reason` için DB CHECK kullanılmayacaktır. Çünkü dışlama gerekçeleri ileride genişleyebilir.

---

## 6.5 `candidate_discoveries`

Candidate öncesi hafif staging tablosudur. Ham permütasyonlar, manuel girişler ve dış kaynak adayları önce buraya yazılır.

| Kolon              | Tip         | Nullable | Default       | Constraint / Not                                                     |
| ------------------ | ----------- | -------- | ------------- | -------------------------------------------------------------------- |
| id                 | BIGSERIAL   | NOT NULL | auto          | PRIMARY KEY                                                          |
| entity_id          | BIGINT      | NOT NULL | —             | FK → entities(id), ON DELETE RESTRICT                                |
| asset_id           | BIGINT      | NULL     | —             | FK → assets(id), ON DELETE RESTRICT                                  |
| value              | TEXT        | NOT NULL | —             | Ham domain/URL                                                       |
| normalized_value   | TEXT        | NOT NULL | —             | Duplicate guard                                                      |
| source             | TEXT        | NOT NULL | 'permutation' | permutation / ct_log / whois_feed / manual_bulk / complaint / manual |
| dns_status         | TEXT        | NOT NULL | 'pending'     | pending / active / inactive / error                                  |
| http_status_code   | INT         | NULL     | —             | HTTP yanıt kodu                                                      |
| skip_reason        | TEXT        | NULL     | —             | whitelisted / duplicate / invalid_format                             |
| failed_check_count | INT         | NOT NULL | 0             | Sadece inactive/error durumunda artar                                |
| last_checked_at    | TIMESTAMPTZ | NULL     | —             | Son kontrol zamanı                                                   |
| next_check_at      | TIMESTAMPTZ | NULL     | —             | Recheck zamanı                                                       |
| created_at         | TIMESTAMPTZ | NOT NULL | now()         | —                                                                    |
| updated_at         | TIMESTAMPTZ | NOT NULL | now()         | Trigger ile güncellenir                                              |

Final kararlar:

```text
candidate_id yok.
promoted_at yok.
Terfi bağı candidate tarafındaki discovery_id ile kurulacak.
```

UNIQUE:

```sql
UNIQUE (entity_id, normalized_value)
```

CHECK:

```sql
dns_status IN ('pending', 'active', 'inactive', 'error')
skip_reason IS NULL OR skip_reason IN ('whitelisted', 'duplicate', 'invalid_format')
failed_check_count >= 0
http_status_code IS NULL OR (http_status_code >= 100 AND http_status_code <= 599)
```

Not:

`source` için DB CHECK kullanılmayacaktır. Kaynak tipleri ileride genişleyebilir.

Bu tablo hafif tutulacaktır. JSONB, HTML, DOM, screenshot veya OCR çıktısı içermez.

---

## 6.6 `candidates`

Gerçek analiz pipeline’ına alınmış adayları tutar. Ham keşif havuzu değildir.

| Kolon            | Tip         | Nullable | Default     | Constraint / Not                                                                 |
| ---------------- | ----------- | -------- | ----------- | -------------------------------------------------------------------------------- |
| id               | BIGSERIAL   | NOT NULL | auto        | PRIMARY KEY                                                                      |
| entity_id        | BIGINT      | NOT NULL | —           | FK → entities(id), ON DELETE RESTRICT                                            |
| discovery_id     | BIGINT      | NOT NULL | —           | FK → candidate_discoveries(id), ON DELETE RESTRICT                               |
| source           | TEXT        | NOT NULL | —           | manual / permutation / ct_log / whois_feed / complaint / …                       |
| value            | TEXT        | NOT NULL | —           | Tam URL/FQDN                                                                     |
| normalized_value | TEXT        | NOT NULL | —           | Candidate seviyesinde duplicate backstop                                         |
| status           | TEXT        | NOT NULL | 'validated' | validated / crawled / analyzed / scored / reviewed / closed / eliminated / error |
| metadata         | JSONB       | NOT NULL | '{}'::jsonb | Küçük provenance                                                                 |
| discovered_at    | TIMESTAMPTZ | NOT NULL | now()       | İlk keşif zamanı                                                                 |
| created_at       | TIMESTAMPTZ | NOT NULL | now()       | —                                                                                |
| updated_at       | TIMESTAMPTZ | NOT NULL | now()       | Trigger ile güncellenir                                                          |

Final kararlar:

```text
discovery_id NOT NULL.
normalized_value eklenecek.
discovered_at NOT NULL DEFAULT now().
whitelisted status yok.
```

Gerekçe:

* Manual dahil bütün input’lar önce `candidate_discoveries` staging üzerinden geçmelidir.
* Candidate doğrudan oluşturulmamalıdır.
* PGMQ retry / eşzamanlı promote durumunda DB seviyesinde duplicate backstop gerekir.
* Uygulama discovery.created_at değerini discovered_at’e kopyalayabilir; vermezse DB default now() fallback sağlar.
* Whitelist candidate öncesi staging aşamasında yakalanır.

CHECK:

```sql
status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed', 'closed', 'eliminated', 'error')
```

UNIQUE partial index:

```sql
UNIQUE (entity_id, normalized_value)
WHERE status NOT IN ('closed', 'eliminated')
```

Not:

`source` için DB CHECK kullanılmayacaktır. Kaynak tipleri ileride genişleyebilir.

---

## 6.7 `blob_storage`

Büyük binary/text içerikleri PostgreSQL içinde tutan append-only storage tablosudur.

| Kolon        | Tip         | Nullable | Default | Constraint / Not                                                                      |
| ------------ | ----------- | -------- | ------- | ------------------------------------------------------------------------------------- |
| id           | BIGSERIAL   | NOT NULL | auto    | PRIMARY KEY                                                                           |
| storage_ref  | TEXT        | NOT NULL | —       | UNIQUE                                                                                |
| file_type    | TEXT        | NOT NULL | —       | html_archive / screenshot / dom_snapshot / ocr_output / favicon / logo / crawl_bundle |
| content_type | TEXT        | NOT NULL | —       | text/html / image/png / image/jpeg / application/json / text/plain                    |
| data         | BYTEA       | NOT NULL | —       | Gerçek binary/text içerik                                                             |
| size_bytes   | BIGINT      | NOT NULL | —       | Dosya boyutu                                                                          |
| content_hash | TEXT        | NULL     | —       | SHA-256                                                                               |
| compression  | TEXT        | NOT NULL | 'none'  | none / gzip                                                                           |
| created_at   | TIMESTAMPTZ | NOT NULL | now()   | —                                                                                     |

UNIQUE:

```sql
UNIQUE (storage_ref)
```

CHECK:

```sql
file_type IN ('html_archive', 'screenshot', 'dom_snapshot', 'ocr_output', 'favicon', 'logo', 'crawl_bundle')
compression IN ('none', 'gzip')
size_bytes >= 0
```

PostgreSQL storage ayarı:

```sql
ALTER TABLE blob_storage ALTER COLUMN data SET STORAGE EXTERNAL;
```

Fiziksel FK yoktur. İlişki `storage_ref` string standardı ve `StorageService` write-time validation ile yönetilecektir.

`content_type` için DB CHECK kullanılmayacaktır. MIME type listesi genişleyebilir.

---

## 6.8 `crawl_results`

Crawler’ın fetch çıktısını tutar. Immutable tablodur.

| Kolon            | Tip         | Nullable | Default     | Constraint / Not                        |
| ---------------- | ----------- | -------- | ----------- | --------------------------------------- |
| id               | BIGSERIAL   | NOT NULL | auto        | PRIMARY KEY                             |
| candidate_id     | BIGINT      | NOT NULL | —           | FK → candidates(id), ON DELETE RESTRICT |
| http_status      | INT         | NOT NULL | —           | HTTP yanıt kodu                         |
| redirect_chain   | JSONB       | NOT NULL | '[]'::jsonb | Redirect zinciri                        |
| final_url        | TEXT        | NOT NULL | —           | Son URL                                 |
| resolved_ip      | TEXT        | NULL     | —           | IP                                      |
| asn              | TEXT        | NULL     | —           | ASN                                     |
| asn_org          | TEXT        | NULL     | —           | ASN organizasyonu                       |
| hosting_provider | TEXT        | NULL     | —           | Hosting provider                        |
| ip_country       | TEXT        | NULL     | —           | Ülke kodu                               |
| storage_ref      | TEXT        | NOT NULL | —           | crawl_bundle manifest ref               |
| metadata         | JSONB       | NOT NULL | '{}'::jsonb | Slim telemetry                          |
| crawled_at       | TIMESTAMPTZ | NOT NULL | —           | Crawl zamanı                            |
| created_at       | TIMESTAMPTZ | NOT NULL | now()       | —                                       |

CHECK:

```sql
http_status >= 100 AND http_status <= 599
jsonb_typeof(redirect_chain) = 'array'
```

Not:

`storage_ref`, `blob_storage` içinde `file_type = 'crawl_bundle'` olan manifest kaydına işaret eder. Fiziksel FK konmaz.

---

## 6.9 `page_features`

Crawl sonucundan türetilen küçük yapısal DOM özetidir. Immutable tablodur.

| Kolon              | Tip         | Nullable | Default     | Constraint / Not                           |
| ------------------ | ----------- | -------- | ----------- | ------------------------------------------ |
| id                 | BIGSERIAL   | NOT NULL | auto        | PRIMARY KEY                                |
| crawl_result_id    | BIGINT      | NOT NULL | —           | FK → crawl_results(id), ON DELETE RESTRICT |
| title              | TEXT        | NULL     | —           | Sayfa başlığı                              |
| has_form           | BOOLEAN     | NOT NULL | false       | Form var mı                                |
| has_password_input | BOOLEAN     | NOT NULL | false       | Şifre alanı var mı                         |
| brand_name_found   | BOOLEAN     | NOT NULL | false       | Marka adı bulundu mu                       |
| dom_summary        | JSONB       | NOT NULL | '{}'::jsonb | Küçük yapısal özet                         |
| extractor_version  | TEXT        | NOT NULL | —           | Extraction versiyonu                       |
| created_at         | TIMESTAMPTZ | NOT NULL | now()       | —                                          |

UNIQUE:

```sql
UNIQUE (crawl_result_id, extractor_version)
```

MVP migration aşamasında `dom_summary` için DB seviyesinde boyut CHECK'i uygulanmayacaktır.

---

## 6.10 `candidate_asset_matches`

Adayın resmi asset’e ne kadar benzediğini tutar. Immutable tablodur.

| Kolon            | Tip          | Nullable | Default     | Constraint / Not                                                                    |
| ---------------- | ------------ | -------- | ----------- | ----------------------------------------------------------------------------------- |
| id               | BIGSERIAL    | NOT NULL | auto        | PRIMARY KEY                                                                         |
| candidate_id     | BIGINT       | NOT NULL | —           | FK → candidates(id), ON DELETE RESTRICT                                             |
| asset_id         | BIGINT       | NOT NULL | —           | FK → assets(id), ON DELETE RESTRICT                                                 |
| crawl_result_id  | BIGINT       | NULL     | —           | FK → crawl_results(id), ON DELETE RESTRICT; domain_similarity için NULL olabilir    |
| match_type       | TEXT         | NOT NULL | —           | domain_similarity / logo_similarity / favicon_similarity / reference_dom_similarity |
| similarity_score | NUMERIC(5,4) | NOT NULL | —           | 0–1 arası                                                                           |
| details          | JSONB        | NOT NULL | '{}'::jsonb | Küçük teknik detay                                                                  |
| created_at       | TIMESTAMPTZ  | NOT NULL | now()       | —                                                                                   |

DB CHECK uygulanacak alan:

```sql
similarity_score >= 0 AND similarity_score <= 1
```

Not:

`match_type` için DB CHECK kullanılmayacaktır. Buradaki örnek değerler MVP'nin beklenen ilk analiz tipleridir; yeni analiz tipleri eklendiğinde migration gerektirmemesi için alan DB seviyesinde esnek bırakılır.

---

## 6.11 `detection_signals`

Sayfanın içinden çıkan, asset’e referans vermeyen sinyalleri tutar. Immutable tablodur.

| Kolon           | Tip          | Nullable | Default     | Constraint / Not                                                                                                           |
| --------------- | ------------ | -------- | ----------- | -------------------------------------------------------------------------------------------------------------------------- |
| id              | BIGSERIAL    | NOT NULL | auto        | PRIMARY KEY                                                                                                                |
| candidate_id    | BIGINT       | NOT NULL | —           | FK → candidates(id), ON DELETE RESTRICT                                                                                    |
| crawl_result_id | BIGINT       | NULL     | —           | FK → crawl_results(id), ON DELETE RESTRICT                                                                                 |
| signal_type     | TEXT         | NOT NULL | —           | form_detected / password_input_detected / ocr_brand_match / brand_keyword_found / suspicious_login_text / html_fingerprint |
| score           | NUMERIC(5,4) | NOT NULL | —           | 0–1 arası                                                                                                                  |
| details         | JSONB        | NOT NULL | '{}'::jsonb | Küçük sinyal özeti                                                                                                         |
| metadata        | JSONB        | NOT NULL | '{}'::jsonb | Küçük teknik metadata                                                                                                      |
| created_at      | TIMESTAMPTZ  | NOT NULL | now()       | —                                                                                                                          |

DB CHECK uygulanacak alan:

```sql
score >= 0 AND score <= 1
```

Not:

`signal_type` için DB CHECK kullanılmayacaktır. Buradaki örnek değerler MVP'nin beklenen ilk sinyal tipleridir; crawler/analysis geliştikçe yeni sinyal tipleri ekleneceği için alan DB seviyesinde esnek bırakılır.

---

## 6.12 `risk_scores`

Toplanan sinyallerden üretilen nihai risk kararını tutar. Immutable tablodur.

| Kolon            | Tip          | Nullable | Default     | Constraint / Not                           |
| ---------------- | ------------ | -------- | ----------- | ------------------------------------------ |
| id               | BIGSERIAL    | NOT NULL | auto        | PRIMARY KEY                                |
| candidate_id     | BIGINT       | NOT NULL | —           | FK → candidates(id), ON DELETE RESTRICT    |
| crawl_result_id  | BIGINT       | NULL     | —           | FK → crawl_results(id), ON DELETE RESTRICT |
| total_score      | NUMERIC(5,4) | NOT NULL | —           | 0–1 arası                                  |
| verdict          | TEXT         | NOT NULL | —           | clean / suspicious / malicious             |
| confidence       | NUMERIC(5,4) | NULL     | —           | 0–1 arası, nullable                        |
| reasons          | JSONB        | NOT NULL | '{}'::jsonb | Kısa karar gerekçesi                       |
| llm_summary      | TEXT         | NULL     | —           | Plus / opsiyonel açıklama                  |
| rule_set_version | TEXT         | NOT NULL | —           | Kural seti versiyonu                       |
| created_at       | TIMESTAMPTZ  | NOT NULL | now()       | —                                          |

CHECK:

```sql
total_score >= 0 AND total_score <= 1
confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
verdict IN ('clean', 'suspicious', 'malicious')
```

---

## 6.13 `rule_results`

Risk skoruna katkı veren kuralların breakdown tablosudur. Immutable tablodur.

| Kolon         | Tip          | Nullable | Default | Constraint / Not                         |
| ------------- | ------------ | -------- | ------- | ---------------------------------------- |
| id            | BIGSERIAL    | NOT NULL | auto    | PRIMARY KEY                              |
| risk_score_id | BIGINT       | NOT NULL | —       | FK → risk_scores(id), ON DELETE RESTRICT |
| rule_code     | TEXT         | NOT NULL | —       | Kural kodu                               |
| weight        | NUMERIC(5,4) | NOT NULL | —       | 0–1 arası katkı ağırlığı                 |
| detail        | JSONB        | NULL     | —       | Küçük detay                              |
| created_at    | TIMESTAMPTZ  | NOT NULL | now()   | —                                        |

CHECK:

```sql
weight >= 0 AND weight <= 1
```

Önemli:

```text
fired kolonu yoktur.
Bu tablo zaten yalnızca skora katkı veren / ateşleyen kuralları tutar.
Migration’da WHERE fired = true index predikatı kullanılmayacaktır.
```

`rule_code` için DB CHECK kullanılmayacaktır. Kural setleri ileride genişleyebilir.

---

## 6.14 `reviews`

İnsan analist kararlarını tutar. Append-only tablodur.

| Kolon         | Tip         | Nullable | Default | Constraint / Not                             |
| ------------- | ----------- | -------- | ------- | -------------------------------------------- |
| id            | BIGSERIAL   | NOT NULL | auto    | PRIMARY KEY                                  |
| candidate_id  | BIGINT      | NOT NULL | —       | FK → candidates(id), ON DELETE RESTRICT      |
| risk_score_id | BIGINT      | NULL     | —       | FK → risk_scores(id), ON DELETE RESTRICT     |
| reviewer      | TEXT        | NOT NULL | —       | Analist                                      |
| decision      | TEXT        | NOT NULL | —       | confirmed / false_positive / needs_more_info |
| notes         | TEXT        | NULL     | —       | Analist notu                                 |
| reviewed_at   | TIMESTAMPTZ | NOT NULL | —       | İnceleme zamanı                              |
| created_at    | TIMESTAMPTZ | NOT NULL | now()   | —                                            |

CHECK:

```sql
decision IN ('confirmed', 'false_positive', 'needs_more_info')
```

Not:

En son review satırı en güncel karar kabul edilir.

---

## 6.15 `cases`

Onaylanmış tehdit için açılan vaka / aksiyon kaydıdır. Mutable tablodur.

| Kolon            | Tip         | Nullable | Default | Constraint / Not                                    |
| ---------------- | ----------- | -------- | ------- | --------------------------------------------------- |
| id               | BIGSERIAL   | NOT NULL | auto    | PRIMARY KEY                                         |
| candidate_id     | BIGINT      | NOT NULL | —       | FK → candidates(id), ON DELETE RESTRICT             |
| review_id        | BIGINT      | NOT NULL | —       | FK → reviews(id), ON DELETE RESTRICT                |
| status           | TEXT        | NOT NULL | 'open'  | open / takedown_requested / closed / false_positive |
| priority         | TEXT        | NULL     | —       | low / medium / high                                 |
| takedown_sent_at | TIMESTAMPTZ | NULL     | —       | Mock/log zamanı                                     |
| notes            | TEXT        | NULL     | —       | Vaka notu                                           |
| created_at       | TIMESTAMPTZ | NOT NULL | now()   | —                                                   |
| updated_at       | TIMESTAMPTZ | NOT NULL | now()   | Trigger ile güncellenir                             |

Final karar:

```text
review_id NOT NULL.
```

Gerekçe:

MVP’de case her zaman human review sonrası açılır. Review olmadan case açılması kanıt/aksiyon zincirini zayıflatır.

CHECK:

```sql
status IN ('open', 'takedown_requested', 'closed', 'false_positive')
priority IS NULL OR priority IN ('low', 'medium', 'high')
```

Aktif tek case kuralı:

```sql
UNIQUE (candidate_id)
WHERE status IN ('open', 'takedown_requested')
```

İleride otomatik case açma istenirse sistem reviewer ile otomatik review satırı oluşturulabilir veya yeni bir migration ile case opening source modeli eklenebilir.

---

## 6.16 `evidence_files`

Kanıt dosyalarının metadata / storage reference tablosudur. Immutable tablodur.

| Kolon           | Tip         | Nullable | Default | Constraint / Not                                                       |
| --------------- | ----------- | -------- | ------- | ---------------------------------------------------------------------- |
| id              | BIGSERIAL   | NOT NULL | auto    | PRIMARY KEY                                                            |
| candidate_id    | BIGINT      | NOT NULL | —       | FK → candidates(id), ON DELETE RESTRICT                                |
| crawl_result_id | BIGINT      | NULL     | —       | FK → crawl_results(id), ON DELETE RESTRICT                             |
| file_type       | TEXT        | NOT NULL | —       | screenshot / html_archive / dom_snapshot / ocr_output / favicon / logo |
| storage_ref     | TEXT        | NOT NULL | —       | Tekil kanıt dosyası referansı                                          |
| content_hash    | TEXT        | NULL     | —       | SHA-256                                                                |
| captured_at     | TIMESTAMPTZ | NOT NULL | —       | Dosyanın üretildiği an                                                 |
| created_at      | TIMESTAMPTZ | NOT NULL | now()   | —                                                                      |

CHECK:

```sql
file_type IN ('screenshot', 'html_archive', 'dom_snapshot', 'ocr_output', 'favicon', 'logo')
```

Not:

```text
case_id yoktur.
Bu case'in kanıtı = case → candidate_id → evidence_files
```

MVP’de case-specific frozen evidence set yoktur. Candidate closed olduktan sonra aynı candidate için yeni evidence yazılmaması application service tarafından korunur.

İleride curated/frozen evidence gerekirse `case_evidence` join tablosu eklenebilir.

---

# 7. Foreign Key Tam Liste

Tüm foreign key ilişkilerinde final davranış:

```sql
ON DELETE RESTRICT
```

| Alan                                    | Referans                  | Null     |
| --------------------------------------- | ------------------------- | -------- |
| asset_groups.entity_id                  | entities(id)              | NOT NULL |
| assets.entity_id                        | entities(id)              | NOT NULL |
| assets.asset_group_id                   | asset_groups(id)          | NULL     |
| exclusions.entity_id                    | entities(id)              | NULL     |
| candidate_discoveries.entity_id         | entities(id)              | NOT NULL |
| candidate_discoveries.asset_id          | assets(id)                | NULL     |
| candidates.entity_id                    | entities(id)              | NOT NULL |
| candidates.discovery_id                 | candidate_discoveries(id) | NOT NULL |
| crawl_results.candidate_id              | candidates(id)            | NOT NULL |
| page_features.crawl_result_id           | crawl_results(id)         | NOT NULL |
| candidate_asset_matches.candidate_id    | candidates(id)            | NOT NULL |
| candidate_asset_matches.asset_id        | assets(id)                | NOT NULL |
| candidate_asset_matches.crawl_result_id | crawl_results(id)         | NULL     |
| detection_signals.candidate_id          | candidates(id)            | NOT NULL |
| detection_signals.crawl_result_id       | crawl_results(id)         | NULL     |
| risk_scores.candidate_id                | candidates(id)            | NOT NULL |
| risk_scores.crawl_result_id             | crawl_results(id)         | NULL     |
| rule_results.risk_score_id              | risk_scores(id)           | NOT NULL |
| reviews.candidate_id                    | candidates(id)            | NOT NULL |
| reviews.risk_score_id                   | risk_scores(id)           | NULL     |
| cases.candidate_id                      | candidates(id)            | NOT NULL |
| cases.review_id                         | reviews(id)               | NOT NULL |
| evidence_files.candidate_id             | candidates(id)            | NOT NULL |
| evidence_files.crawl_result_id          | crawl_results(id)         | NULL     |

---

# 8. Unique Constraint / Unique Index Listesi

| Tablo                 | Kolonlar                                                                      | Amaç                                           |
| --------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------- |
| asset_groups          | entity_id, name                                                               | Aynı entity altında aynı grup tekrar oluşmasın |
| assets                | entity_id, asset_type, value WHERE is_active = true                           | Aktif asset duplicate olmasın                  |
| exclusions            | entity_id, value, match_type WHERE is_active = true AND entity_id IS NOT NULL | Entity bazlı aktif exclusion duplicate olmasın |
| exclusions            | value, match_type WHERE is_active = true AND entity_id IS NULL                | Global aktif exclusion duplicate olmasın       |
| candidate_discoveries | entity_id, normalized_value                                                   | Discovery duplicate guard                      |
| candidates            | entity_id, normalized_value WHERE status NOT IN ('closed','eliminated')       | Candidate duplicate backstop                   |
| page_features         | crawl_result_id, extractor_version                                            | Extraction idempotency                         |
| blob_storage          | storage_ref                                                                   | Storage reference tekilliği                    |
| cases                 | candidate_id WHERE status IN ('open','takedown_requested')                    | Aynı candidate için tek aktif case             |
| evidence_files        | storage_ref                                                                   | Evidence dosya referansı tekilliği             |

---

# 9. Önerilen Index Listesi

```sql
-- asset_groups
CREATE UNIQUE INDEX uq_asset_groups_entity_name
ON asset_groups(entity_id, name);

-- assets
CREATE UNIQUE INDEX uq_assets_active_entity_type_value
ON assets(entity_id, asset_type, value)
WHERE is_active = true;

CREATE INDEX ix_assets_entity_type_value
ON assets(entity_id, asset_type, value);

-- exclusions
CREATE INDEX ix_exclusions_entity_active
ON exclusions(entity_id, is_active);

CREATE UNIQUE INDEX uq_exclusions_entity_active_value_match
ON exclusions(entity_id, value, match_type)
WHERE is_active = true AND entity_id IS NOT NULL;

CREATE UNIQUE INDEX uq_exclusions_global_active_value_match
ON exclusions(value, match_type)
WHERE is_active = true AND entity_id IS NULL;

-- candidate_discoveries
CREATE UNIQUE INDEX uq_candidate_discoveries_entity_normalized
ON candidate_discoveries(entity_id, normalized_value);

CREATE INDEX ix_candidate_discoveries_pending
ON candidate_discoveries(entity_id, created_at)
WHERE dns_status = 'pending' AND skip_reason IS NULL;

CREATE INDEX ix_candidate_discoveries_recheck
ON candidate_discoveries(next_check_at)
WHERE dns_status IN ('inactive', 'error')
  AND skip_reason IS NULL
  AND next_check_at IS NOT NULL;

CREATE INDEX ix_candidate_discoveries_asset
ON candidate_discoveries(asset_id)
WHERE asset_id IS NOT NULL;

-- candidates
CREATE UNIQUE INDEX uq_candidates_entity_normalized_active
ON candidates(entity_id, normalized_value)
WHERE status NOT IN ('closed', 'eliminated');

CREATE INDEX ix_candidates_entity_active_created
ON candidates(entity_id, created_at)
WHERE status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed');

CREATE INDEX ix_candidates_status_updated_active
ON candidates(status, updated_at)
WHERE status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed');

-- blob_storage
CREATE INDEX ix_blob_storage_content_hash
ON blob_storage(content_hash)
WHERE content_hash IS NOT NULL;

CREATE INDEX ix_blob_storage_created_at
ON blob_storage(created_at);

-- crawl_results
CREATE INDEX ix_crawl_results_candidate_crawled
ON crawl_results(candidate_id, crawled_at);

-- page_features
CREATE INDEX ix_page_features_crawl_result
ON page_features(crawl_result_id);

CREATE UNIQUE INDEX uq_page_features_crawl_extractor
ON page_features(crawl_result_id, extractor_version);

-- candidate_asset_matches
CREATE INDEX ix_candidate_asset_matches_candidate
ON candidate_asset_matches(candidate_id);

-- detection_signals
CREATE INDEX ix_detection_signals_candidate_signal
ON detection_signals(candidate_id, signal_type);

-- risk_scores
CREATE INDEX ix_risk_scores_candidate_created
ON risk_scores(candidate_id, created_at);

-- rule_results
CREATE INDEX ix_rule_results_risk_score
ON rule_results(risk_score_id);

CREATE INDEX ix_rule_results_code
ON rule_results(rule_code);

-- reviews
CREATE INDEX ix_reviews_candidate_reviewed_at
ON reviews(candidate_id, reviewed_at DESC);

-- cases
CREATE INDEX ix_cases_candidate_status
ON cases(candidate_id, status);

CREATE UNIQUE INDEX uq_cases_candidate_active
ON cases(candidate_id)
WHERE status IN ('open', 'takedown_requested');

-- evidence_files
CREATE INDEX ix_evidence_files_candidate
ON evidence_files(candidate_id);

CREATE UNIQUE INDEX uq_evidence_files_storage_ref
ON evidence_files(storage_ref);

CREATE INDEX ix_evidence_files_content_hash
ON evidence_files(content_hash)
WHERE content_hash IS NOT NULL;
```

---

# 10. CHECK Constraint Listesi

## Lifecycle / critical TEXT CHECK’leri

Bu liste yalnızca DB seviyesinde kısıtlanacak kritik lifecycle / integrity alanlarını kapsar. `candidate_asset_matches.match_type`, `detection_signals.signal_type`, `rule_results.rule_code` ve `source` alanları bu listeye bilinçli olarak dahil edilmez; bu alanlar uygulama/domain tarafında yönetilecek açık uçlu tiplerdir.

```sql
-- assets
asset_type IN ('domain', 'subdomain')

-- exclusions
match_type IN ('exact', 'registrable_domain', 'subdomain_of', 'pattern')

-- candidate_discoveries
dns_status IN ('pending', 'active', 'inactive', 'error')
skip_reason IS NULL OR skip_reason IN ('whitelisted', 'duplicate', 'invalid_format')
http_status_code IS NULL OR (http_status_code >= 100 AND http_status_code <= 599)

-- candidates
status IN ('validated', 'crawled', 'analyzed', 'scored', 'reviewed', 'closed', 'eliminated', 'error')

-- risk_scores
verdict IN ('clean', 'suspicious', 'malicious')

-- reviews
decision IN ('confirmed', 'false_positive', 'needs_more_info')

-- cases
status IN ('open', 'takedown_requested', 'closed', 'false_positive')
priority IS NULL OR priority IN ('low', 'medium', 'high')

-- evidence_files
file_type IN ('screenshot', 'html_archive', 'dom_snapshot', 'ocr_output', 'favicon', 'logo')

-- blob_storage
file_type IN ('html_archive', 'screenshot', 'dom_snapshot', 'ocr_output', 'favicon', 'logo', 'crawl_bundle')
compression IN ('none', 'gzip')
```

## JSONB / TEXT boyut guardrail kararı

MVP migration aşamasında JSONB/TEXT boyut guardrail CHECK constraint'leri uygulanmayacaktır.

Amaç, gerçek crawl ve analysis verisinin hangi tablolarda ve hangi kolonlarda büyüdüğünü gözlemleyebilmektir. Hard limitler baştan migration'a konursa ölçüm yerine insert hatası üretilir.

Korunacak constraint türleri:

```text
FK ilişkileri
NOT NULL alanlar
ON DELETE RESTRICT
lifecycle CHECK'leri
score aralıkları
HTTP status aralıkları
negatif değer engelleri
```

`redirect_chain` için `jsonb_typeof(redirect_chain) = 'array'` CHECK'i kalacaktır; bu boyut değil veri bütünlüğü kuralıdır.

Boyut guardrail'leri ileride veri gözlemlendikten sonra ayrı bir hardening migration olarak eklenebilir:

```text
V007__hardening_guardrails_up.sql
V007__hardening_guardrails_down.sql
```

## Skor constraint’leri

```sql
-- candidate_asset_matches
similarity_score >= 0 AND similarity_score <= 1

-- detection_signals
score >= 0 AND score <= 1

-- risk_scores
total_score >= 0 AND total_score <= 1
confidence IS NULL OR (confidence >= 0 AND confidence <= 1)

-- rule_results
weight >= 0 AND weight <= 1
```

## Diğer constraint’ler

```sql
candidate_discoveries.failed_check_count >= 0
candidate_discoveries.http_status_code IS NULL OR (candidate_discoveries.http_status_code >= 100 AND candidate_discoveries.http_status_code <= 599)
blob_storage.size_bytes >= 0
crawl_results.http_status >= 100 AND crawl_results.http_status <= 599
```

## Normalizasyon Politikası

`candidate_discoveries.normalized_value` ve `candidates.normalized_value` DB seviyesinde tutulur ve duplicate guard bu alanlar üzerinden çalışır.

`assets.value` ve `exclusions.value` için MVP'de ayrıca expression index kullanılmayacaktır. Bu alanlarda lower-case / scheme temizleme / registrable-domain standardizasyonu application service sorumluluğudur. İleride DB seviyesinde ek güvence gerekirse `lower(value)` tabanlı partial unique index ayrı migration ile eklenebilir.

---

# 11. updated_at Trigger Kararı

Ortak trigger function `V001__asset_layer_up.sql` içinde oluşturulacaktır.

```sql
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

Trigger bağlanacak tablolar:

```text
entities
asset_groups
assets
exclusions
candidate_discoveries
candidates
cases
```

Her tablo için:

```sql
CREATE TRIGGER trg_<table_name>_set_updated_at
BEFORE UPDATE ON <table_name>
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
```

Bu sayede application service `updated_at` set etmeyi unutsa bile mutable tabloların güncelleme zamanı doğru kalır.

---

# 12. Tablo Oluşturma Sırası

Foreign key bağımlılıklarına göre önerilen sıra:

```text
1. entities
2. asset_groups
3. assets
4. exclusions
5. candidate_discoveries
6. candidates
7. blob_storage
8. crawl_results
9. page_features
10. candidate_asset_matches
11. detection_signals
12. risk_scores
13. rule_results
14. reviews
15. cases
16. evidence_files
```

`blob_storage` fiziksel FK ile bağlı değildir; ancak `storage_ref` standardı nedeniyle crawl/evidence tablolarından önce oluşturulması daha temizdir.

---

# 13. Down Migration Silme Sırası

Down migration’da ters sırayla silinmelidir:

```text
1. evidence_files
2. cases
3. reviews
4. rule_results
5. risk_scores
6. detection_signals
7. candidate_asset_matches
8. page_features
9. crawl_results
10. blob_storage
11. candidates
12. candidate_discoveries
13. exclusions
14. assets
15. asset_groups
16. entities
```

Trigger’lar ve trigger function da down migration’da silinmelidir.

Önerilen sıra:

```text
1. Trigger’ları drop et
2. Tabloları ters FK sırasıyla drop et
3. set_updated_at() function’ını drop et
```

Index’ler tablo drop edildiğinde otomatik düşeceği için ayrıca drop edilmeleri şart değildir. Ancak explicit drop tercih edilirse index’ler tablolardan önce düşürülmelidir.

---

# 14. PGMQ / JobQueue Kararı

`outbox_jobs` tablosu oluşturulmayacaktır.

MVP queue isimleri:

| Queue                      | İş                                 |
| -------------------------- | ---------------------------------- |
| candidate_validation_queue | DNS/HTTP kontrolü                  |
| crawl_queue                | Crawler işleri                     |
| feature_extraction_queue   | Page feature çıkarımı              |
| similarity_queue           | Domain/logo/favicon/DOM similarity |
| risk_scoring_queue         | Risk skoru hesaplama               |

Uygulama kodu doğrudan PGMQ fonksiyonlarına bağlanmayacaktır.

Beklenen yapı:

```text
Application Service
→ JobQueue interface
→ PGMQ implementation
```

İleri fazda Kafka’ya geçilirse iş mantığı değişmeden yalnızca `JobQueue` implementasyonu değiştirilecektir.

---

# 15. Final Karar Özeti

Bu migration öncesi final şemada alınan kritik kararlar:

```text
1. PostgreSQL ENUM kullanılmayacak; TEXT + kod tarafında enum/codec kullanılacak.
2. Kritik lifecycle TEXT alanlarında CHECK kullanılacak.
3. Score alanları NUMERIC(5,4) olacak.
4. Tüm score alanlarına CHECK 0..1 eklenecek.
5. Tüm foreign key ilişkilerinde ON DELETE RESTRICT kullanılacak.
6. candidates.discovery_id NOT NULL olacak.
7. candidates.normalized_value eklenecek.
8. candidates.discovered_at NOT NULL DEFAULT now() olacak.
9. candidates.status içinde whitelisted olmayacak.
10. Whitelist normalde candidate_discoveries.skip_reason = 'whitelisted' ile temsil edilecek.
11. Candidate’a geldikten sonra masum bulunan kayıt reviews.decision = 'false_positive' veya candidates.status = 'eliminated' ile yönetilecek.
12. cases.review_id NOT NULL olacak.
13. Case her zaman review sonrası açılacak.
14. MVP’de evidence package candidate üzerinden türetilecek; case-specific frozen evidence set olmayacak.
15. İleride frozen evidence gerekirse case_evidence join tablosu eklenecek.
16. JSONB alanlar mümkün olduğunca NOT NULL DEFAULT '{}'::jsonb olacak.
17. Büyük dosyalar JSONB’ye gömülmeyecek; blob_storage + storage_ref kullanılacak.
18. blob_storage ile evidence/crawl tabloları arasında fiziksel FK olmayacak.
19. outbox_jobs tablosu olmayacak; PGMQ + JobQueue kullanılacak.
20. updated_at alanları PostgreSQL trigger ile otomatik güncellenecek.
21. rule_results.fired kolonu olmayacak.
22. candidate_discoveries içinde candidate_id/promoted_at olmayacak.
23. Tüm inputlar önce candidate_discoveries staging üzerinden geçecek.
24. Aktif asset, aktif exclusion, aktif case ve active candidate duplicate durumları unique index’lerle korunacak.
```
