# Mona DRP — MVP Core Uçtan Uca Happy Path (v5 — finalize)

**Kapsam:** Yalnızca MVP **Core** (Domain Identity Protection). Korunan dijital varlık şimdilik tek tip: bir domain/URL.

**Veri modeli:** Güncel şema

**Hariç:** MVP Plus (CT Log, WHOIS feed, HTML fingerprint, LLM özeti, gelişmiş recurrence) ve İleri Faz.

Bu akış, her şeyin yolunda gittiği (happy path) ideal senaryoyu izler. Örnek: korunan marka **Akbank** (`akbank.com`), tespit edilen sahte site **`akbank-guvenli-giris.com`**.

**Bir önceki sürüme göre değişenler (finalize kararları):**
1. **Status enum'u tek tip `-ed`.** Nihai akış: `validated → crawled → analyzed → scored → reviewed → closed` (+ `eliminated`, `error`). `discovered` candidates'tan çıktı (staging üstleniyor); `review_needed` çıktı (`scored` yeterli).
2. **`candidate_id` + `promoted_at` `candidate_discoveries`'ten çıkarıldı.** Yerine `candidates.discovery_id FK → candidate_discoveries.id` eklendi. Terfi bağı artık candidate tarafında.
3. **`check_count → failed_check_count` rename.** Yalnızca `inactive`/`error`'da artar.
4. **Crawl-öncesi `domain_similarity` kapısı kaldırıldı.** Terfi eden her aday crawl'lanır; `domain_similarity` yalnızca post-crawl, diğer benzerliklerle aynı adımda sinyal olarak yazılır.
5. **`candidates.asset_id` yok.** Owner anchor = `entity_id`; kaynak asset'e `discovery_id → candidate_discoveries.asset_id` ile gidilir; fiili benzerlik M:N olarak `candidate_asset_matches`'te.

---

* **Korunan marka/kişi kaydedilir**
  * `entities` tablosuna yeni kayıt atılır
    * `name = "Akbank"`, `type = "brand"` (type string; kodda enum+codec)

* **O marka/kişiye ait korunacak dijital varlık kaydedilir** (şimdilik sadece domain/URL)
  * `assets` tablosuna kayıt atılır
    * `entity_id` (zorunlu), `asset_type = "domain"`, `value = "akbank.com"`, `asset_group_id` (NULLABLE — MVP'de boş kalabilir)
    * Logo/favicon ayrı asset değildir; `assets.metadata` (JSONB, <2KB) içinde **yalnızca referans** olarak tutulur: `homepage_url`, `login_page_url`, `logo_ref`, `favicon_ref`, `reference_dom_summary`. Binary/base64 içerik metadata'ya gömülmez; gerekiyorsa `blob_storage` üzerinden `storage_ref` ile saklanır. Domain/DOM/görsel similarity bu referansları okur.

* **Korunan domaine benzeyen aday domain/URL'ler üretilir.** Adaylar farklı kaynaklardan gelebilir: permutation, Şikayetvar, manuel giriş vb.
  * **Ham adayların hepsi önce `candidate_discoveries` staging tablosuna yazılır** (analiz tablosu değildir; hafif tutulur).
    * `entity_id` (zorunlu owner anchor), `asset_id` (permütasyon kaynağı asset — generation provenance; ct_log/complaint gibi dış kaynaklarda NULL), `value` (ham domain/URL), `normalized_value` (lowercase + scheme çıkarılmış — duplicate guard bunun üzerinden çalışır), `source` (permutation | ct_log | whois_feed | manual_bulk | complaint)
    * `dns_status = "pending"`, `failed_check_count = 0`
    * Duplicate guard: `candidate_discoveries(entity_id, normalized_value)` UNIQUE. `candidates` artık ham lookup/duplicate tablosu değildir.

  * **Her adayın gerçekten geçerli bir aday olup olmadığı kontrol edilir** (DNS/HTTP). Bir aday t anında geçerliyken t+1'de geçersiz olabilir (saldırgan domaini değiştirir); benzer şekilde t'de geçersizken t+1'de geçerli hale gelebilir (önceden ürettiğimiz aday domain saldırganca kullanılmaya başlanabilir).
    * **Önce `exclusions` tablosu okunur.** Aday aktif bir exclusion ile eşleşirse `candidates`'a terfi etmez; `candidate_discoveries.skip_reason = "whitelisted"` yazılarak staging'de kalır.
    * Exclusion'a takılmayan aday için **DNS/HTTP kontrolü** yapılır:
      * **Aktif** (DNS çözülüyor + HTTP erişilebilir): `dns_status = "active"`, `http_status_code` doldurulur → aday `candidates`'a **terfi eder**.
      * **İnaktif**: `dns_status = "inactive"`; `failed_check_count` artırılır, `next_check_at` exponential backoff ile ileri atılır (`next_check_at = now() + gecikme(failed_check_count)`; sayı arttıkça gecikme katlanır). Periyodik recheck'e kalır; t+1'de canlanırsa terfi eder.
      * **Hata**: `dns_status = "error"`; `failed_check_count` artırılır, backoff ile yeniden denenir.
    * Not: `failed_check_count` yalnızca **başarısız** (inactive/error) denemelerde artar; aktif aday terfi edip recheck döngüsünden çıktığı için onunki artmaz (zaten okunmaz).

* **Terfi eden aday `candidates`'a yazılır ve analiz pipeline'ına girer**
  * `candidates` tablosunda satır: `entity_id`, `source`, `value` (tam FQDN/URL — registrable domain değil), `status = "validated"`, **`discovery_id` (FK → `candidate_discoveries.id`)**, `metadata` (JSONB <2KB; sadece küçük provenance: `complaint_id`, `ct_cert_id`…), `discovered_at` (ilk keşif zamanı — provenance).
    * **Terfi bağı candidate tarafında:** "bu keşif terfi etti mi?" = `discovery_id = X` olan candidate var mı (tek index lookup). `candidate_discoveries`'e geri yazım yok.
    * **Kaynak asset'e erişim:** candidate → `discovery_id` → `candidate_discoveries.asset_id`. (Bu yüzden `candidates`'ta ayrı `asset_id` yok.)
  * `candidates.status` değerleri: `validated → crawled → analyzed → scored → reviewed → closed`; yan/terminal: `eliminated`, `error` (pipeline adımı patlarsa stuck kalmamak için), `whitelisted` (yalnızca candidate'a gelmiş bir adayın istisnai manuel kapatması için).
  * Not: `validated` = DNS/HTTP geçip terfi etmiş, crawl-öncesi **asenkron bekleme** durumu. Crawl hemen yapılmayabilir; aday `crawl_queue`'da bekler. "Şu an crawl ediliyor" gibi in-flight bilgi status'ta tutulmaz — onu **PGMQ** taşır, başarısızlığı `error` taşır.

### Bir candidate'in `validated`'dan `closed`'a — pipeline'i nasıl işler

Bundan sonrası asenkron bir worker zinciridir. Üç şeyi ayrı tutmak resmi netleştirir: **status** = candidate'in ulaştığı son durak (milestone); **kuyruk (PGMQ)** = onu bir sonraki adıma taşıyan, "şu an işleniyor" bilgisini tutan mekanizma; **tablolar** = her adımın ürettiği kalıcı kanıt. Her aşamada bir worker kuyruktan mesajı alır, işini yapıp DB'ye yazar, status'u bir sonraki milestone'a taşır ve bir sonraki kuyruğa mesaj atar. Aday `validated` durumunda `crawl_queue`'da bekliyor; sırayla:

**1. Crawl — `validated → crawled`** · *"Site gerçekte ne içeriyor?"*
Crawler worker siteyi izole ortamda (Browserless/Docker-VM) açar. Terfi eden **her** aday crawl'lanır; ayrı bir crawl-öncesi benzerlik kapısı yoktur (kararı `candidate_discoveries`'in active + exclusion filtresi vermiştir). İki tür çıktı üretir:
  * **Gözlemler** → `crawl_results` satırı (FETCH — site açılmadan üretilemez): `http_status`, `redirect_chain` (<4KB, max 30 hop), `final_url`, `resolved_ip`, `asn`, `asn_org`, `hosting_provider`, `ip_country` (artık JSONB değil **kolon**), `storage_ref`, `crawled_at`. Slim telemetri (`ssl_info`, `server_header`, `content_type`, `fetch_duration_ms`…) `metadata`'da (<4KB).
  * **Ham dosyalar** → `blob_storage`: HTML / DOM / screenshot her biri ayrı satır (`data` BYTEA; text gzip'li, PNG/JPEG `none`; `content_hash` SHA-256 + `size_bytes`). Bir de bunları toplayan **manifest** satırı (`file_type = "crawl_bundle"`, içinde `html_ref`/`dom_ref`/`screenshot_ref`). `crawl_results.storage_ref` bu manifeste işaret eder. Format: `pg://evidence/{candidate_id}/{crawl_run_id}/{file_type}.{ext}`.
  * Status `crawled` olur, `feature_extraction_queue`'ya mesaj düşer.

**2. Feature extraction — (status sabit kalır)** · *"Ham DOM'dan hangi yapısal özetler çıkar?"*
Worker, crawl'da blob'a yazılan ham DOM'u okur, ondan **küçük yapısal özet** türetir → `page_features` satırı: `title`, `has_form`, `has_password_input`, `brand_name_found`, `dom_summary` (JSONB <8KB — `form_count`, `input_count`, `password_input_count`, `button_count`, `external_script_count`…), `extractor_version`. Tam DOM kopyalanmaz; sadece sayısal özeti yazılır (tam DOM `crawl_results.storage_ref` üzerinden blob'da kalır). Idempotency: `page_features(crawl_result_id, extractor_version)` UNIQUE; mantık değişirse yeni `extractor_version`'lı satır yazılır, re-fetch yok. Bu hafif veri, sonraki analiz adımlarının okuyacağı girdidir.

**3. Analiz — iki paralel bacak — `crawled → analyzed`**
Candidate iki farklı açıdan incelenir; **ikisi de bittiğinde** status `analyzed` olur.
  * **(a) Asset'e benzerlik** — *"Korunan asset'e ne kadar benziyor?"* → `candidate_asset_matches`, her çerçeve ayrı satır:
    * `domain_similarity` (≈0.88), `logo_similarity` (≈0.91), `favicon_similarity` (≈0.94), `reference_dom_similarity` (≈0.82 — `page_features.dom_summary`'yi `crawl_result_id` üzerinden okur). Her biri `similarity_score` + `details` (<4KB).
    * Not: `domain_similarity` crawl gerektirmez (`crawl_result_id` NULLABLE) ama artık ayrı kapı değil; diğer benzerliklerle **bu adımda birlikte** yazılır. Risk skorundaki `domain·0.25` ağırlığını besler.
  * **(b) Sayfa-içi sinyaller** — *"Sayfa kendi başına ne kadar şüpheli?"* (asset'e referans vermez) → `detection_signals`:
    * `form_detected` (1.0), `password_input_detected` (1.0), `ocr_brand_match` (≈0.95) — her biri `score`, `details` (<4KB).
    * Form/şifre alanı `page_features`'tan; OCR blob'taki screenshot'tan okunur. Ham OCR çıktısı details'a değil, `blob_storage`'a (`evidence_files.storage_ref`) gider.

**4. Risk scoring — `analyzed → scored`** · *"Tüm sinyaller tek karara nasıl iner?"*
Worker sinyalleri ağırlıklandırıp tek skora indirir: `0.88·0.25 + 0.91·0.20 + 0.82·0.15 + form·0.20 + ocr·0.10 ≈ **0.91**` → `verdict = malicious` (eşikler: `≥0.70 malicious`, `≥0.40 suspicious`, else `clean`). İki tablo **tek transaction'da** yazılır (breakdown'sız bir total hiçbir an gözlemlenmez):
  * `risk_scores`: `total_score = 0.91`, `verdict`, `confidence`, `reasons` (JSONB slim <4KB), `rule_set_version`, `crawl_result_id`. (`llm_summary` TEXT, Plus → boş.)
  * `rule_results`: skoru oluşturan her kuralın katkısı ayrı satır — `risk_score_id` (NOT NULL FK → `risk_scores.id`), `rule_code` (`domain_similarity_high` +0.25, `logo_favicon_high`, `login_form_detected` +0.20, `dom_structural_high`, `ocr_brand_found`), `weight`, `detail` (<4KB). "0.91 nereden geldi"nin açıklamasıdır (total magic number kalmasın diye). FK candidate'e değil `risk_scores`'a gider — bir candidate birden çok kez skorlanabildiğinden her breakdown kendi koşumuna çivilenir.
  * `scored` aynı zamanda "analiste hazır" demektir (ayrı `review_needed` statüsü yok).

**5. Human review — `scored → reviewed`** · *Burada otomasyon durur, insan girer.*
Analist server-rendered ekranda (Play Twirl, React yok) screenshot'ı, similarity skorlarını, OCR/form sonucunu ve verdict'i görür, karar verir → `reviews` satırı (append-only): `candidate_id`, `risk_score_id`, `reviewer = "analyst_01"`, `decision = "confirmed"`, `notes`, `reviewed_at`. Bu adım false-positive yakalamak içindir; append-only olduğu için en son satır en güncel karar kabul edilir.

**6. Case + takedown — `reviewed → closed`** · *Onaylandığı için aksiyon başlar.*
  * `cases` satırı: `candidate_id`, `review_id`, `status = "takedown_requested"`, `priority = "high"`, `takedown_sent_at` (MVP'de gerçek API değil, mock/log).
  * **Kanıt paketi** → `evidence_files`: crawl'da blob'a yazılmış screenshot / html_archive / dom_snapshot / ocr_output dosyalarına **işaret eden** satırlar açılır (içerik kopyalanmaz, referans verilir) + `risk_scores`(0.91) + `reviews`(confirmed). `evidence_files` immutable / `case_id` taşımaz; pakete erişim **`case → candidate_id → evidence_files`** (ortak `candidate_id`) ile türetilir. Her satır: `file_type`, `storage_ref` (tekil dosya referansı), `content_hash` (= `blob_storage.content_hash`; recurrence/dedup için), `timestamp`. Gerçek içerik `evidence_files`'ta değil `blob_storage.data`'dadır.
  * Status `closed`. Happy path biter.

**Büyük resim:**

```
validated ─[crawl]→ crawled ─[extract+analyze]→ analyzed ─[score]→ scored ─[insan]→ reviewed ─[case]→ closed
   │             │                   │                            │             │            │
crawl_queue   crawl_results     candidate_asset_matches        risk_scores    reviews      cases
              blob_storage      detection_signals              rule_results                evidence_files
                                page_features
```

Özet: crawl'dan sonrası kabaca **"topla → iki açıdan analiz et → tek skora indir → insana onaylat → vaka aç ve kanıtı bağla."** Her ok bir PGMQ mesajıdır; her durak bir status milestone'u; her kutu o adımın ürettiği kalıcı kanıttır.

---

## İş kuyruğu — PGMQ / JobQueue (outbox_jobs'un yerini aldı)

İş akışı `outbox_jobs` tablosu yerine **PostgreSQL üzerinde çalışan PGMQ tabanlı JobQueue** ile ilerler. Uygulama kodu doğrudan PGMQ'ye değil bir `JobQueue` interface'ine bağlıdır; ileride aynı interface Kafka'ya taşınabilir. Kuyruk mesajları yalnızca `target_type`, `target_id`, `job_type` ve küçük parametreler taşır — HTML/DOM/OCR/screenshot gibi büyük içerik payload'a girmez. Worker'lar **idempotent** çalışır (aynı mesaj tekrar işlense bile aday iki kez promote edilmez, duplicate `crawl_result`/`page_features` oluşmaz).

In-flight ("şu an işleniyor") bilgisi status'ta değil, mesajın kuyruktaki durumunda tutulur; status yalnızca **tamamlanan son durağı** (milestone) gösterir.

| Kuyruk | İş |
|---|---|
| `candidate_validation_queue` | DNS/HTTP kontrolü (`candidate_discoveries` üzerinde) → aktif olanı terfi et |
| `crawl_queue` | Crawler işleri |
| `feature_extraction_queue` | `page_features` çıkarımı |
| `similarity_queue` | domain / logo / favicon / DOM similarity analizi (post-crawl) |
| `risk_scoring_queue` | Risk skoru + rule breakdown hesaplama |

---

## Akış Özeti — Adım → Tablo

| # | Adım | Yazılan tablo(lar) | Status |
|---|---|---|---|
| 1 | Marka tanımlama | `entities` | — |
| 2 | Asset tanımlama | `assets` (+ ops. `asset_groups`) | — |
| 3 | Ham aday üretimi (çok kaynak) | `candidate_discoveries` | `dns_status = pending` |
| 3a | Exclusions + DNS/HTTP (staging) | `candidate_discoveries` (`dns_status`, `skip_reason`, `failed_check_count`, `next_check_at`) | aktif → terfi / inactive → recheck / whitelisted → kalır |
| 4 | Terfi: gerçek aday | `candidates` (`discovery_id → candidate_discoveries`) | → `validated` |
| 5 | Crawl | `crawl_results`, `blob_storage` (html/dom/screenshot + crawl_bundle) | → `crawled` |
| 6 | Page feature extraction | `page_features` | (analiz) |
| 7 | Asset karşılaştırması (domain+logo+favicon+dom, post-crawl) | `candidate_asset_matches` | (analiz) |
| 8 | Sayfa-içi sinyaller | `detection_signals` | → `analyzed` |
| 9 | Risk scoring (atomik: skor + breakdown) | `risk_scores`, `rule_results` | → `scored` |
| 10 | Human review | `reviews` | → `reviewed` |
| 11 | Case + takedown mock | `cases` (+ `evidence_files`, `blob_storage`) | → `closed` |

---

## Notlar

* **Geçerlilik döngüsü `candidate_discoveries`'te yaşar:** İnaktif bir keşif (`dns_status = "inactive"`) `next_check_at` + `failed_check_count` (exponential backoff) ile periyodik recheck'e alınır. t anında ölü olan bir aday t+1'de canlanırsa DNS/HTTP'den geçer ve `candidates`'a terfi eder — yani t→t+1 canlanması staging katmanında, ham `candidates` taraması olmadan yakalanır.
* **`closed` bir vakanın aynı içeriğinin farklı domainle yeniden çıkması** gece-doğrulamanın değil **recurrence monitoring**'in işidir (`evidence_files.content_hash` / `blob_storage.content_hash` üzerinden) — bu MVP Plus / İleri Faz. Saldırgan yeni domain açarsa, bu yeni bir keşif olarak (permutation/Şikayetvar kaynağından) `candidate_discoveries`'ten baştan akışa girer.
* **`error` state'i:** Herhangi bir pipeline adımı (crawl, extraction, scoring) patlarsa `candidates.status = "error"` ile işaretlenir; aday stuck kalmaz, ayrıca ele alınır. Happy path'te bu state'e girilmez.

---