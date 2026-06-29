# Mona DRP — Happy Path Veri Akışı Simülasyonu

Bu belge, happy path'in (v5) her adımında **tablolara fiilen yazılan satırları** gerçekçi örnek değerlerle, DB satır görünümüne yakın markdown tabloları olarak gösterir. Kod/servis detayı yok; yalnızca "ne oluyor → DB'de ne yazılıyor".

**Senaryo:** Korunan marka **Akbank** (`akbank.com`). Tespit edilen sahte site **`akbank-guvenli-giris.com`**.

**Okuma notları:** Uzun değerler kısaltılır (`sha256:a1b2…`, `<gzip ~45KB>`). Zaman damgaları aynı gün (`2026-06-25`) ilerler; yalnızca saat gösterilir. `→ kuyruk` satırları PGMQ mesajını temsil eder. JSONB kolonları hücre içinde tek satır `{…}` olarak verilir.

---

## 0. Kurulum — marka, asset, exclusion

**entities**

| id | name | type | created_at | updated_at |
|---|---|---|---|---|
| 1 | Akbank | brand | 09:00 | 09:00 |

**assets**

| id | entity_id | asset_group_id | asset_type | value | metadata | is_active | created_at | updated_at |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 | NULL | domain | akbank.com | `{homepage_url:"https://www.akbank.com", login_page_url:"https://giris.akbank.com", logo_ref:"pg://ref/akbank/logo.png", favicon_ref:"pg://ref/akbank/favicon.png", reference_dom_summary:{form_count:1,input_count:2,password_input_count:1}}` | true | 09:00 | 09:00 |

**exclusions**

| id | entity_id | value | match_type | reason | is_active | created_by | created_at | updated_at |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 | akbankdirekt.com | exact | owned_unmonitored | true | analyst_01 | 09:01 | 09:01 |

---

## 1. Aday üretimi — ham keşifler `candidate_discoveries`'e yazılır

`akbank.com` permütasyonları **hepsi** önce staging'e düşer (henüz `candidates`'a değil). Üç örnek keşif, ilk yazım anı:

**candidate_discoveries** (ilk yazım)

| id | entity_id | asset_id | value | normalized_value | source | dns_status | http_status_code | skip_reason | failed_check_count | last_checked_at | next_check_at | created_at |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1001 | 1 | 1 | http://akbank-guvenli-giris.com | akbank-guvenli-giris.com | permutation | pending | NULL | NULL | 0 | NULL | NULL | 09:05 |
| 1002 | 1 | 1 | http://akbnk.com | akbnk.com | permutation | pending | NULL | NULL | 0 | NULL | NULL | 09:05 |
| 1003 | 1 | 1 | http://akbankdirekt.com | akbankdirekt.com | permutation | pending | NULL | NULL | 0 | NULL | NULL | 09:05 |

> Duplicate guard: `(entity_id, normalized_value)` UNIQUE.

`→ candidate_validation_queue` : her keşif için doğrulama mesajı.

---

## 2. Exclusion + DNS/HTTP → aktif olan terfi eder

Her keşif önce exclusion'a bakılır, takılmıyorsa DNS/HTTP yapılır. Değişen kolonlar:

**candidate_discoveries** (DNS/HTTP sonrası güncellenen kolonlar)

| id | dns_status | http_status_code | skip_reason | failed_check_count | last_checked_at | next_check_at | updated_at | sonuç |
|---|---|---|---|---|---|---|---|---|
| 1003 | pending | NULL | whitelisted | 0 | NULL | NULL | 09:06 | exclusion'a takıldı, DNS'e hiç gidilmedi → terfi yok |
| 1002 | inactive | NULL | NULL | 1 | 09:06 | 09:21 | 09:06 | DNS çözülmedi → backoff ile recheck'e kaldı |
| 1001 | active | 200 | NULL | 0 | 09:06 | NULL | 09:06 | aktif → **candidates'a terfi** |

**Terfi: yeni `candidates` satırı.** Bağ candidate tarafında (`discovery_id`); staging'e geri yazım yok.

**candidates**

| id | entity_id | source | value | status | discovery_id | metadata | discovered_at | created_at | updated_at |
|---|---|---|---|---|---|---|---|---|---|
| 501 | 1 | permutation | akbank-guvenli-giris.com | validated | 1001 | `{origin_domain:"akbank.com"}` | 09:05 | 09:06 | 09:06 |

> "Terfi etti mi?" = `discovery_id=1001` olan candidate var mı. Kaynak asset: candidate #501 → discovery #1001 → `asset_id=1` (bu yüzden `candidates`'ta `asset_id` yok).

`→ crawl_queue` : `target = candidate #501`.

---

## 3. Crawl — `crawl_results` + `blob_storage`  ·  status `validated → crawled`

Crawl mekanizması siteyi izole ortamda açar. Gözlemler `crawl_results`'a, ham dosyalar + manifest `blob_storage`'a.

**crawl_results**

| id | candidate_id | http_status | redirect_chain | final_url | resolved_ip | asn | asn_org | hosting_provider | ip_country | storage_ref | metadata | crawled_at | created_at |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 7 | 501 | 200 | `[{from:"http://akbank-guvenli-giris.com",to:"https://…/giris",code:301}]` | https://akbank-guvenli-giris.com/giris | 185.231.114.27 | AS204957 | Stark Industries Solutions Ltd | Shinjiru | NL | pg://evidence/501/7/crawl_bundle.json | `{ssl_issuer:"Let's Encrypt",server:"nginx",content_type:"text/html",fetch_duration_ms:1840,blocked_by_bot_protection:false}` | 09:10 | 09:10 |

> `storage_ref` → manifest satırına (#9005) işaret eder, tek tek dosyalara değil.

**blob_storage** (crawl'da yazılan 5 satır — ilk 4 gerçek dosya, sonuncusu manifest)

| id | storage_ref | file_type | content_type | data | size_bytes | content_hash | compression | created_at |
|---|---|---|---|---|---|---|---|---|
| 9001 | pg://evidence/501/7/html_archive.html | html_archive | text/html | `<gzip ~45KB>` | 46180 | sha256:a1b2c3… | gzip | 09:10 |
| 9002 | pg://evidence/501/7/dom_snapshot.json | dom_snapshot | application/json | `<gzip ~18KB>` | 18432 | sha256:b2c3d4… | gzip | 09:10 |
| 9003 | pg://evidence/501/7/screenshot.png | screenshot | image/png | `<png ~210KB>` | 215040 | sha256:c3d4e5… | none | 09:10 |
| 9004 | pg://evidence/501/7/favicon.png | favicon | image/png | `<png ~4KB>` | 4096 | sha256:d4e5f6… | none | 09:10 |
| 9005 | pg://evidence/501/7/crawl_bundle.json | crawl_bundle | application/json | `{html_ref:"…/html_archive.html", dom_ref:"…/dom_snapshot.json", screenshot_ref:"…/screenshot.png", favicon_ref:"…/favicon.png"}` | 280 | sha256:e5f6a7… | none | 09:10 |

**candidates** (güncelleme)

| id | status | updated_at |
|---|---|---|
| 501 | crawled | 09:10 |

`→ feature_extraction_queue` : `target = crawl_result #7`.

---

## 4. Feature extraction — `page_features`

Ham DOM (`blob #9002`) okunur, küçük yapısal özet türetilir. Tam DOM kopyalanmaz.

**page_features**

| id | crawl_result_id | title | has_form | has_password_input | brand_name_found | dom_summary | extractor_version | created_at |
|---|---|---|---|---|---|---|---|---|
| 11 | 7 | Akbank Direkt - Giriş | true | true | true | `{form_count:1,input_count:3,password_input_count:1,button_count:2,external_script_count:4}` | v1.2 | 09:11 |

> Idempotency: `(crawl_result_id=7, extractor_version="v1.2")` UNIQUE.

`→ similarity_queue` : `target = candidate #501`.

---

## 5a. Analiz — asset'e benzerlik — `candidate_asset_matches`

Aday, korunan asset (#1) ile dört çerçeveden karşılaştırılır. *"Korunan asset'e ne kadar benziyor?"*

**candidate_asset_matches**

| id | candidate_id | asset_id | crawl_result_id | match_type | similarity_score | details | created_at |
|---|---|---|---|---|---|---|---|
| 3001 | 501 | 1 | NULL | domain_similarity | 0.88 | `{algorithm:"jaro_winkler",brand_token_match:true,compared:"akbank.com vs akbank-guvenli-giris.com"}` | 09:12 |
| 3002 | 501 | 1 | 7 | logo_similarity | 0.91 | `{method:"phash",hamming_distance:6}` | 09:12 |
| 3003 | 501 | 1 | 7 | favicon_similarity | 0.94 | `{method:"phash",hamming_distance:3}` | 09:12 |
| 3004 | 501 | 1 | 7 | reference_dom_similarity | 0.82 | `{compared:"page_features vs asset.reference_dom_summary",form_layout_match:0.85}` | 09:12 |

> `domain_similarity` crawl gerektirmez → `crawl_result_id = NULL`; diğer üçü crawl çıktısına dayanır → `crawl_result_id = 7`.

---

## 5b. Analiz — sayfa-içi sinyaller — `detection_signals` (+ OCR çıktısı blob'a)

Sayfanın kendi içinden, asset'e referans vermeyen sinyaller. OCR ham çıktısı önce blob'a yazılır, sinyal ona referans verir.

**blob_storage** (analizde eklenen OCR satırı)

| id | storage_ref | file_type | content_type | data | size_bytes | content_hash | compression | created_at |
|---|---|---|---|---|---|---|---|---|
| 9006 | pg://evidence/501/7/ocr_output.txt | ocr_output | text/plain | `<gzip ~2KB>` ("Akbank Direkt \| Giriş Yap \| Şifre …") | 2048 | sha256:f6a7b8… | gzip | 09:12 |

**detection_signals**

| id | candidate_id | crawl_result_id | signal_type | score | details | metadata | created_at |
|---|---|---|---|---|---|---|---|
| 4001 | 501 | 7 | form_detected | 1.0 | `{form_count:1}` | `{source:"page_features"}` | 09:12 |
| 4002 | 501 | 7 | password_input_detected | 1.0 | `{password_input_count:1}` | `{source:"page_features"}` | 09:12 |
| 4003 | 501 | 7 | ocr_brand_match | 0.95 | `{matched_brand:"Akbank",matched_text_snippet:"Akbank Direkt Giriş",confidence:0.95,source_ref:"…/ocr_output.txt"}` | `{engine:"tesseract"}` | 09:12 |

**candidates** (güncelleme — her iki analiz tarafı da yazıldı)

| id | status | updated_at |
|---|---|---|
| 501 | analyzed | 09:12 |

`→ risk_scoring_queue` : `target = candidate #501`.

---

## 6. Risk scoring — `risk_scores` + `rule_results`  ·  status `analyzed → scored`

Tüm sinyaller ağırlıklandırılıp tek skora indirilir. İki tablo **tek transaction'da** yazılır.

**Skor hesabı (ham ağırlıklı katkılar):**
```
domain  0.88 × 0.25 = 0.220
logo    0.91 × 0.20 = 0.182
dom     0.82 × 0.15 = 0.123
form    1.00 × 0.20 = 0.200
ocr     0.95 × 0.10 = 0.095
                     -------
ham toplam          = 0.820
ağırlık toplamı     = 0.90   (fingerprint 0.10 Plus → yok)
normalize           = 0.820 / 0.90 ≈ 0.91   → verdict "malicious" (≥0.70)
```

**risk_scores**

| id | candidate_id | crawl_result_id | total_score | verdict | confidence | reasons | llm_summary | rule_set_version | created_at |
|---|---|---|---|---|---|---|---|---|---|
| 21 | 501 | 7 | 0.91 | malicious | 0.93 | `{top_rules:["domain_similarity_high","logo_favicon_high","login_form_detected"],normalized_from:0.820}` | NULL | rs_v1.0 | 09:13 |

**rule_results** (her ateşleyen kural ayrı satır, FK → risk_scores #21)

| id | risk_score_id | rule_code | weight | detail | created_at |
|---|---|---|---|---|---|
| 5001 | 21 | domain_similarity_high | 0.25 | `{signal:0.88,contribution:0.220}` | 09:13 |
| 5002 | 21 | logo_favicon_high | 0.20 | `{signal:0.91,contribution:0.182}` | 09:13 |
| 5003 | 21 | dom_structural_high | 0.15 | `{signal:0.82,contribution:0.123}` | 09:13 |
| 5004 | 21 | login_form_detected | 0.20 | `{signal:1.00,contribution:0.200}` | 09:13 |
| 5005 | 21 | ocr_brand_found | 0.10 | `{signal:0.95,contribution:0.095}` | 09:13 |

**candidates** (güncelleme — "analiste hazır")

| id | status | updated_at |
|---|---|---|
| 501 | scored | 09:13 |

---

## 7. Human review — `reviews`  ·  status `scored → reviewed`

Analist server-rendered ekranda screenshot + skorları + verdict'i görür, karar verir.

**reviews**

| id | candidate_id | risk_score_id | reviewer | decision | notes | reviewed_at | created_at |
|---|---|---|---|---|---|---|---|
| 31 | 501 | 21 | analyst_01 | confirmed | Login formu + Akbank logosu + OCR marka eşleşmesi; net phishing. | 09:30 | 09:30 |

> `reviews` append-only; en son satır en güncel karar.

**candidates** (güncelleme)

| id | status | updated_at |
|---|---|---|
| 501 | reviewed | 09:30 |

---

## 8. Case + kanıt paketi — `cases` + `evidence_files`  ·  status `reviewed → closed`

Onaylanan tehdit için vaka açılır; kanıt dosyaları referansla pakete bağlanır; takedown loglanır.

**cases**

| id | candidate_id | review_id | status | priority | takedown_sent_at | notes | created_at | updated_at |
|---|---|---|---|---|---|---|---|---|
| 41 | 501 | 31 | takedown_requested | high | 09:32 | Registrar + hosting abuse başvurusu (mock) gönderildi. | 09:32 | 09:32 |

**evidence_files** (her dosya için bir satır; içerik kopyalanmaz, blob'a işaret eder; `content_hash` blob ile aynı)

| id | candidate_id | crawl_result_id | file_type | storage_ref | content_hash | timestamp | created_at |
|---|---|---|---|---|---|---|---|
| 6001 | 501 | 7 | screenshot | pg://evidence/501/7/screenshot.png | sha256:c3d4e5… (=blob #9003) | 09:10 | 09:32 |
| 6002 | 501 | 7 | html_archive | pg://evidence/501/7/html_archive.html | sha256:a1b2c3… (=blob #9001) | 09:10 | 09:32 |
| 6003 | 501 | 7 | dom_snapshot | pg://evidence/501/7/dom_snapshot.json | sha256:b2c3d4… (=blob #9002) | 09:10 | 09:32 |
| 6004 | 501 | 7 | ocr_output | pg://evidence/501/7/ocr_output.txt | sha256:f6a7b8… (=blob #9006) | 09:12 | 09:32 |

> Tam paket: yukarıdaki dosyalar + `risk_scores #21` (0.91) + `reviews #31` (confirmed). `evidence_files`'ta `case_id` yok; "bu case'in kanıtı" = `case #41 → candidate_id 501 → evidence_files`.

**candidates** (güncelleme — son durak)

| id | status | updated_at |
|---|---|---|
| 501 | closed ✦ | 09:32 |

---

## Status izi — candidate #501

| status | zaman | adım |
|---|---|---|
| validated | 09:06 | terfi |
| crawled | 09:10 | crawl |
| analyzed | 09:12 | analiz |
| scored | 09:13 | scoring |
| reviewed | 09:30 | insan |
| closed | 09:32 | case |

## Diğer iki keşfin sonu

| discovery | sonuç |
|---|---|
| 1002 (akbnk.com) | inactive, failed_check_count=1, next_check_at=09:21 → recheck'e kaldı |
| 1003 (akbankdirekt.com) | skip_reason=whitelisted → candidates'a hiç girmedi |

## blob_storage — bu crawl sonrası nihai durum

| id | file_type | storage_ref | content_hash | yazıldığı adım |
|---|---|---|---|---|
| 9001 | html_archive | …/html_archive.html | a1b2c3… | Crawl |
| 9002 | dom_snapshot | …/dom_snapshot.json | b2c3d4… | Crawl |
| 9003 | screenshot | …/screenshot.png | c3d4e5… | Crawl |
| 9004 | favicon | …/favicon.png | d4e5f6… | Crawl |
| 9005 | crawl_bundle (manifest) | …/crawl_bundle.json | e5f6a7… | Crawl |
| 9006 | ocr_output | …/ocr_output.txt | f6a7b8… | Analiz (OCR) |

> `crawl_results #7.storage_ref` → `#9005` (manifest) → `#9001/9002/9003/9004`. `evidence_files` ise tekil dosyalara (`#9001/9002/9003/9006`) doğrudan işaret eder.

---

## Bu akışta üretilen tüm satırlar

| Tablo | Yazılan satır(lar) |
|---|---|
| entities | #1 |
| assets | #1 |
| exclusions | #1 |
| candidate_discoveries | #1001 (terfi), #1002 (inactive), #1003 (whitelisted) |
| candidates | #501 |
| crawl_results | #7 |
| blob_storage | #9001–#9006 |
| page_features | #11 |
| candidate_asset_matches | #3001–#3004 |
| detection_signals | #4001–#4003 |
| risk_scores | #21 |
| rule_results | #5001–#5005 |
| reviews | #31 |
| cases | #41 |
| evidence_files | #6001–#6004 |
