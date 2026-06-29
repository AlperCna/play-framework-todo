# Constitution.md — Açıklama

> **Amaç:** `.specify/memory/constitution.md` dosyasının ne anlattığını adım adım, soyutlamadan
> detaya inerek açıklamak. Bu **yaşayan bir dokümandır** — her turda daha derin soyutlama katmanı
> eklenecek. Şu anki katman: **Katman 0 — Belgenin anatomisi** (kuralların *içeriği* değil, dokümanın
> *neyi nerede* düzenlediği).
>
> İlgili: [constitution.md](.specify/memory/constitution.md) · [CLAUDE.md](CLAUDE.md) ·
> [agentic-pipeline-yol-haritasi.md](docs/research_documents/agentic-pipeline-yol-haritasi.md)

---

## Katman 0 — Belgenin Anatomisi

constitution.md, **agentic pipeline'ın kalıcı hafızası**dır: pipeline'ın pazarlık edilemez kurallarını
tutar ve `analyze` + `scope-reviewer` üzerinden zorlanır. Belge 6 bloktan oluşur.

### 1. Sync Impact Report (en üstteki yorum bloğu)
İnsana/araca görünmeyen bir **changelog / meta** alanı. Anlattıkları:
- Bu belgenin başka bir projeden (biggerphish / .NET) **uyarlandığı**, neden 1.0.0'dan başladığı.
- İlke numaralarına `scope-reviewer` ve `analyze`'ın **numarayla** referans verdiği — bu yüzden bir
  ilke silinip yeniden numaralandırılınca bu dosyalar da senkronlanmalı.
- Hangi bağımlı dosyaların hâlâ güncellenmesi gerektiği (deferred TODO).

*Kural değil, bakım notu.*

### 2. Core Principles (I–VI) — belgenin kalbi
Altı numaralı **MUST ilkesi**; pipeline'ın "pazarlık edilemez" davranış kuralları. Soyut gruplama:
- **Mimari & sınırlar** (modülerlik, kapsam) → I, II
- **Kod kalitesi** (tek sorumluluk) → III
- **Veri & altyapı disiplini** → IV
- **Okunabilirlik / yorum** → V
- **Görev tipine göre "doğru"nun tanımı** → VI

Her ilke aynı şablonda: *kural(lar) + "Rationale" (neden)*.

### 3. Additional Constraints — Platform & Stack (MUST)
Genel ilkelerin altında, **Mona DRP'ye özgü somut teknik kısıtlar**. İki grup:
- **Stack & kod konvansiyonları** (Scala/Play sürümü, domain modelleme stili, persistence yerleşimi)
- **Platform & pipeline invariant'ları** (staging, queue/storage soyutlaması, açıklanabilir skorlama,
  şema & migration disiplini)

İlkelerden farkı: I–VI *evrensel prensip*, bu blok *bu projenin somut kararları*.

### 4. Development Workflow & Quality Gates
Pipeline'ın **nasıl işleyeceğine** dair süreç kuralları: SDD faz sırası + her kapıda insan onayı,
`analyze`'ın implement'ten önce zorunluluğu (CRITICAL → blok), faz başına git checkpoint, ScalaTest'in
her adımda geçmesi.

### 5. Governance
Belgenin **kendini yönetme** kuralları: constitution ad-hoc pratikleri ezer; değişiklik için ne gerekir
(versiyon bump + bağımlı dosya senkronu); versiyonlama politikası (MAJOR/MINOR/PATCH); uyumu kimin
denetlediği (`analyze` + `scope-reviewer`).

### 6. Footer (metadata)
Tek satır: **Version / Ratified / Last Amended** — sürüm izlenebilirliği.

---

**Akış özeti:** meta (1) → evrensel ilkeler (2) → projeye özgü kısıtlar (3) → süreç kapıları (4) →
belgenin kendi yönetimi (5) → sürüm (6).

---

## Katman 1 — Her Maddenin "Ne"liği (İşlevi)

> Burada constitution'ın **her bloğundaki tek tek maddelerin işlevini** not ediyoruz: madde *neyi*
> zorluyor, *neyi* engelliyor. (Nasıl uygulandığı sonraki katmanlarda.) Sıra: önce Core Principles
> (I–VI), ardından Additional Constraints, Development Workflow, Governance ve son olarak meta bloklar
> (Sync Impact Report + Footer).


### Core Principles (I–VI)

#### Mimari & sınırlar → I, II

**İlke I — Architecture Boundary Conformance** (modül içi katmanlar + modüller arası sınır)
- *Katman yerleşimi & bağımlılık yönü:* DRP kodu `app/drp/<module>/` altında `domain / application
  (+ports) / infrastructure / web / workers` katmanlarına yerleşir; bağımlılıklar **yalnızca içe** akar.
  → İşlev: modül içindeki yön kuralını tanımlar.
- *Saf domain:* `domain` katmanında Play/Slick/HTTP/JSON/DB tipi bulunamaz.
  → İşlev: iş kuralını altyapıdan izole eder.
- *İnce web:* controller iş kararı vermez, veriye doğrudan dokunmaz (hep `application` üzerinden);
  Twirl view = typed view-model'in **saf render'ı**, domain/persistence tipi sızmaz.
  → İşlev: sunum katmanını "aptal" tutar.
- *Modüller arası tek-yön:* `asset → discovery → candidate → crawl → analysis → risk → review →
  casework`; `shared` / `platform.*` yatay yardımcı. Yukarı/döngüsel bağımlılık yok.
  → İşlev: pipeline yönünü modül grafiğine sabitler.
- *Single-Writer & port (her iki yön de port'tan):* modüller arası **hem yazma hem okuma** sahibin
  `application/ports/` arayüzünden geçer. Yazma → write port (asıl yazmayı sahip yapar; her tabloyu **tek
  modül** yazar). Okuma → read port, typed bir **read-model** döndürür (sahibin domain/Slick tipi sızmaz);
  başka modülün tablosuna **doğrudan Slick sorgusu yasak**. Modül kendi tablolarına doğrudan erişir.
  → İşlev: veri sahipliğini netleştirir; mapping'i tek kaynakta tutar (tekrar yok) ve şema coupling'ini önler.
- *Mekanizma tekrarı yok:* yeni kod kurulu kalıpları (smart-constructor domain → `Either[DomainError,_]`,
  `ServiceResult`, repository port + Slick adapter, modül başına Guice modülü) **yeniden kullanır**;
  paralel/ikinci bir yol kurmaz.
  → İşlev: "tek doğru yol" disiplini.
- *`app/todo/` dokunulmazlığı:* geçici iskele; üstüne DRP inşa edilmez, yeniden şekillendirilmez.
  → İşlev: kaldırılacak referans kodu dondurur.

**İlke II — Scope Boundary** (kapsam sınırı)
- *Sadece kapsam içi:* yalnızca aktif spec'in "in scope" dediği dosya/davranış değiştirilebilir.
  → İşlev: diff'i spec ile 1:1 eşler.
- *Kapsam dışına çıkış = dur ve sor:* gerekiyorsa eskalasyon, sessizce ilerleme yok.
  → İşlev: scope creep'i görünür kılar.

#### Kod kalitesi → III

**İlke III — Single Responsibility**
- *Metot tek iş yapar:* ölçü uzunluk değil, **sorumluluk sayısı**.
  → İşlev: değişikliği yerel ve review'ı kolay tutar.

#### Veri & altyapı disiplini → IV

**İlke IV — Data Access Discipline**
- *Döngü içinde DB çağrısı yok:* gereken küme toplu çekilir, bellekte işlenir.
  → İşlev: N+1 / per-iterasyon sorgu defektini kapatır.
- *Pagination = varsayılan:* veriyle büyüyen her liste okuması `Page`/`PageRequest` kullanır. Sınırsız
  (`SELECT *`) okuma yalnızca küçük/sabit-sınırlı küme için (lookup/enum tablosu, bir parent'ın az
  çocuğu) ve gerekçesi belirtilerek serbest — asla varsayılan değil.
  → İşlev: "%99 pagination" kuralı; unbounded okumayı istisna + gerekçeye iter.
- *Eşzamanlılık + idempotency:* read/write yolları race condition'ı açıkça ele alır; worker'lar
  idempotent (aynı mesaj iki kez işlenince duplicate/çift terfi olmaz).
  → İşlev: async pipeline'ın sessiz doğruluk hatalarını kapatır.
- *Büyük içerik JSONB/payload'a girmez:* HTML/DOM/screenshot/OCR/binary → `blob_storage` + `storage_ref`.
  → İşlev: şişkin satır/mesaj sorununu önler, içeriği referansla taşır.

#### Okunabilirlik / yorum → V

**İlke V — Abstraction Reflected in Comments**
- *Public soyutlama "ne" yorumu taşır:* imza tek başına açık değilse paket/dosya/trait/public metot
  kısa bir "ne yapar" notu içerir.
  → İşlev: soyutlama niyetini tersine mühendislik gerektirmeden okunur kılar.
- *Inline yorum sadece "neden":* HOW'un satır-satır anlatımı yasak; WHY yalnızca non-obvious'sa.
  → İşlev: kodu tekrar eden gürültü yorumları engeller.

#### Görev tipine göre "doğru"nun tanımı → VI

**İlke VI — Type-Conditional Behavior Preservation** (6 `Type` değeri "doğru"yu seçer)
- *Feature Implementation:* greenfield/yeni davranış — I (kurulu mekanizmalar, duplicate yok) ve II
  (kapsam) dışında preservation kısıtı yok; ölçüt "yeni davranış spec'teki gibi çalışır".
  → İşlev: baseline tip; ekstra koruma kısıtı getirmez.
- *Refactoring / Performance Optimization:* gözlemlenebilir dış davranış (girdi/çıktı, public API **+
  port** sözleşmesi, yan etki, veri şekli) **birebir** korunur; yeni FR / fırsatçı iyileştirme yasak.
  → İşlev: davranış-koruyan tiplerde sürüklenmeyi yasaklar.
- *Bug Fixing:* yalnızca hatalı davranış değişir, komşu davranış korunur.
  → İşlev: düzeltmeyi izole eder.
- *Feature Enhancement:* yalnızca beyan edilen delta (önce→sonra) değişir; "to preserve" listesi korunur.
  → İşlev: eklemeyi delta ile sınırlar.
- *Code Understanding:* kod üretilmez (implement çalışmaz), çıktı analizdir.
  → İşlev: kod-üretmeyen tipi diff'siz tutar.

### Additional Constraints — Platform & Stack (MUST)

> Evrensel ilkelerden (I–VI) farkı: bunlar **Mona DRP'ye özgü somut kararlar**. İki grup.

**Stack & kod konvansiyonları**
- *Stack + DI + view:* Scala 2.13.18 / Play 2.9; her DRP modülü kendi Guice `Module`'ünü açar, kökte
  `DrpModule` kompoze eder; view'lar Twirl server-rendered, React/SPA yok.
  → İşlev: teknoloji ve modül-wiring tabanını sabitler.
- *Domain modelleme:* immutable `final case class` + smart constructor; validation `Either[DomainError,_]`;
  kapalı kümeler `sealed trait` + ADT (derleyici exhaustive zorlar); illegal-states-unrepresentable
  (bare `String`/`Int` değil, anlamlı wrapper tip); servis `Future[Either[…]]`/`ServiceResult`; domain'de IO yok.
  → İşlev: "yanlış durum derlenemesin" diye tip sistemini kullanır.
- *Persistence yerleşimi:* PostgreSQL + `slick-pg`; repository **port** `<module>/application`'da, Slick
  adapter `<module>/infrastructure`'da; her modül kendi tablo tanımını taşır (tek büyüyen `Tables` facade yok).
  → İşlev: persistence'ı port arkasına alır, tablo sahipliğini modülde tutar.

**Platform & pipeline invariant'ları**
- *Staging disiplini:* her girdi (manuel/permütasyon/feed) önce `candidate_discoveries`'e girer;
  `candidates`'a doğrudan yazılmaz; terfi yalnız exclusion + DNS/HTTP sonrası, `candidates.discovery_id`
  (NOT NULL) ile izlenir.
  → İşlev: tek giriş kapısı; her aday'ın kaynağını/iz zincirini garanti eder.
- *Queue/storage soyutlaması:* iş kodu `JobQueue` ve `StorageService` arayüzlerine bağlanır (PGMQ
  fonksiyonlarına / `blob_storage` SQL'ine doğrudan değil); mesaj yalnız `target_type`/`target_id`/`job_type`
  + küçük param taşır, HTML/DOM/binary taşımaz.
  → İşlev: altyapıyı değiştirilebilir tutar (PGMQ→Kafka, blob→S3); mesajı ince tutar.
- *Açıklanabilir skorlama:* karar motoru açıklanabilir rule-based skorlama; LLM karar verici değil (en
  fazla opsiyonel özet); `risk_scores` + `rule_results` tek transaction'da yazılır.
  → İşlev: skoru denetlenebilir kılar; "breakdown'suz toplam" durumunu imkânsızlaştırır.
- *Şema invariant'ları:* PostgreSQL ENUM yok (lifecycle alanları TEXT + CHECK + kod tarafı enum/codec);
  tüm FK `ON DELETE RESTRICT`; skor alanları `NUMERIC(5,4)` + `CHECK 0..1`; `candidates.status`'ta
  `whitelisted` yok, `evidence_files`'ta `case_id` yok.
  → İşlev: veri bütünlüğü + kanıt zincirini şema seviyesinde dondurur.
- *Migration disiplini:* şema/veri değişiklikleri elle, versiyonlu SQL (`app/migrations/drp-postgres/`,
  up/down); Evolutions/Flyway yok; uygulama açılışta auto-migrate etmez; ajan migration dosyası yazabilir
  ama **çalıştıramaz** (uygulama = manuel insan adımı).
  → İşlev: şema değişimini insan kontrolünde + geri-alınabilir tutar; ajanın DB'ye dokunmasını engeller.

### Development Workflow & Quality Gates

> Pipeline'ın **nasıl işleyeceğine** dair süreç kuralları (içerik değil, akış).

- *SDD faz akışı + insan onayı:* specify → clarify → plan → tasks → analyze → implement; her kapıda
  insan onayı (CLAUDE.md §10: keşfet→plan→onay→uygula; tek-seferlik dev refactor yok).
  → İşlev: işi onaylı, küçük adımlara böler.
- *analyze ön-koşulu (konvansiyon — otomatik dayatılmaz):* constitution `/speckit-analyze`'ı implement'ten
  **önce** zorunlu kılar; CRITICAL bulgu (herhangi bir MUST ihlali) implementi **bloklamalı** — çözüm
  spec/plan/tasks'ı düzeltmek, constitution'ı sulandırmak değil. ⚠ Bu bir **süreç disiplinidir, teknik
  kilit değil**: `implement` yalnızca tasks.md'yi arar (analyze raporunu kontrol etmez), workflow.yml
  analyze'ı adım olarak içermez, analyze read-only olduğu için fiziksel bloklayamaz → "blok" = insan/ajanın
  rapora uyması. Atlanırsa hata alınmaz.
  → İşlev: kural ihlalini kod üretiminden önce yakalamayı *amaçlayan* kapı — ama denetimi insana/ajana bağlı.
- *Faz başına commit:* her onaylı fazdan sonra git checkpoint; iş modül modül, atomik bağımsız commit'lerle.
  → İşlev: her fazı geri-alınabilir/izlenebilir yapar.
- *Doğrulama (ScalaTest):* `test/` suite'leri her adımda geçmeli; **her yeni repository port'u, Slick
  adapter'ının yanında bir in-memory test adapter ile gelir** (servisi DB'siz unit-testlenebilsin —
  scaffold'un `InMemory*Repository` pattern'i; port'u onsuz eklemek gerekçe ister). Yeni davranış insan
  review + `quickstart.md` ile doğrulanır; rigor sonra sıkılaşacak (önce behavior-koruyan tipler için
  karakterizasyon testleri). ⚠ DB-garantili invariant'lar (constraint/transaction/race) in-memory'de
  test edilmez — integration testi gerektirir.
  → İşlev: her adımda yeşil taban + her port'a DB'siz test yolu; regresyonu erken yakalar.

### Governance

> Belgenin **kendini yönetme** kuralları.

- *Üstünlük:* constitution ad-hoc pratikleri ezer.
  → İşlev: çelişkide tek otorite constitution'dır.
- *Değişiklik şartları:* amendment = dokümante değişiklik + versiyon bump + bağımlı template/agent
  senkronu (özellikle `scope-reviewer.md`).
  → İşlev: kural değişimini izlenebilir + tutarlı tutar (bu turlardaki akışımızın ta kendisi).
- *Versiyonlama:* MAJOR = geriye-uyumsuz ilke kaldırma/yeniden tanımlama; MINOR = yeni ilke/bölüm;
  PATCH = açıklama/ifade.
  → İşlev: değişikliğin ağırlığını sürümden okunur kılar.
- *Uyum denetimi:* `/speckit-analyze` + `scope-reviewer` uyumu doğrular (ikisi de I–VI'ya numarayla
  referans verir); bir ilkeyi esneten karmaşıklık `plan.md` Complexity Tracking'de gerekçelenmeli.
  → İşlev: uyumu iki denetçiye bağlar; istisnayı görünür kılar.

### Meta bloklar (Sync Impact Report + Footer)

> Bu iki bloğun **kuralı/maddesi yok** — meta/bakım blokları. Ama pipeline'da işlevleri var.

**Sync Impact Report** (en üstteki `<!-- … -->` yorumu)
- *Görünmez + kural değil:* HTML yorumu → render'da çıkmaz; insana/araca yönelik meta; sıfır MUST.
  → İşlev: kuralın parçası değil, **bakım kaydı**.
- *Changelog (ne + neden):* her amendment bir blok — versiyon (X→Y), bump gerekçesi (MAJOR/MINOR/PATCH),
  hangi ilke/bölüm değişti.
  → İşlev: constitution geçmişini git arkeolojisi olmadan okunur kılar.
- *"Sync Impact" = etki/senkron kaydı (adının asıl anlamı):* değişen kuralın hangi bağımlı dosyalara
  yayıldığını (`scope-reviewer.md`, `tasks-template.md`, `Constitution-Aciklama.md` …) ve bilerek
  **değiştirilmeyenleri** (örn. specs/001-002 geçmiş kayıtları) kaydeder.
  → İşlev: Governance'ın zorunlu kıldığı "bağımlı senkron"un yapıldığının kanıtı/checklist'i.
- *Deferred TODO:* henüz hizalanmamış ama hizalanması gereken kalemler (örn. başlangıç bloğundaki .NET
  kalıntısı metinler).
  → İşlev: bilinen borcu görünür tutar.
- *Kaynak:* `/speckit-constitution` skill'i bu bloğu üretir/günceller (Spec Kit konvansiyonu).

**Footer** (`**Version** | **Ratified** | **Last Amended**`)
- *Tek satır metadata.*
  → İşlev: yürürlükteki sürüm + ilk onay + son değişiklik tarihini tek bakışta verir; en üstteki son
  amendment'la eşleşir (sürüm izlenebilirliği).

---
