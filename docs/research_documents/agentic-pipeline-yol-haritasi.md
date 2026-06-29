# Uçtan Uca Agentic Geliştirme Pipeline'ı — Yol Haritası

**Yığın:** Claude Code (orkestratör/ajan) + GitHub Spec Kit (SDD iskeleti)
**Hedef kod tabanı:** .NET / C# (mevcut/brownfield)
**Onay modeli:** Her aşamada insan onayı (spec → plan → görevler → kod)
**v1 kapsamı:** Otomatik test yok (review eden sensin), Azure entegrasyonu sonraya bırakıldı

---

## 0. Tek bakışta mimari

Senin elle işlettiğin döngü, Spec Kit'in komut akışına neredeyse birebir oturuyor. Pipeline'ın sabit kalan iskeleti bu dört faz; task tipi değişince **sadece spec'in içeriği** değişir.

| Senin manuel adımın | SDD fazı | Spec Kit komutu | Onay kapısı |
|---|---|---|---|
| Teoriyi anlama + kararlar | (ön hazırlık) | `/speckit.constitution` (bir kez), `/speckit.clarify` | — |
| Workflow çıkarma | Specify | `/speckit.specify` (+ `/checklist`) | ✓ Spec onayı |
| İmplementasyon planı | Plan | `/speckit.plan` | ✓ Plan onayı |
| (görevlere bölme) | Tasks | `/speckit.tasks` (+ `/analyze`) | ✓ Görev onayı |
| İmplementasyon | Implement | `/speckit.implement` | ✓ Kod review + kapsam denetçisi |

Tam akış (kalite kapılı): `constitution → specify → clarify → checklist → plan → tasks → analyze → implement`. `clarify`, `checklist`, `analyze` salt-okunurdur ve hiçbir dosyayı değiştirmez; senin onay kapılarının otomatik destekçileridir.

---

## 1. Araç yığını ve neden bu ikili

**Claude Code** orkestratördür. Repoda `.claude/` klasörü üzerinden konfigüre edilir:
- `CLAUDE.md` — her oturumda yüklenen "davranış sözleşmesi" (proje kuralları, .NET konvansiyonları). Not: bu *bağlam*tır, zorlayıcı bir kural motoru değil; gerçek zorlama subagent izinleri ve hook'larla yapılır.
- `.claude/agents/` — **subagent**'lar. Her biri kendi izole bağlamında, **kısıtlı araç erişimiyle** çalışır (örn. salt-okunur bir denetçi: sadece okuma + git, Edit/Write yok).
- `.claude/settings.json` — izin modları ve **hook**'lar (örn. her düzenlemeden önce bir kapı, sonra `dotnet format`).
- `.mcp.json` — dış entegrasyonlar (ileride Azure DevOps).

**Spec Kit** SDD iskeletini kurar. `specify init` ile repoya `.specify/` klasörü ekler:
- `.specify/memory/constitution.md` — projenin pazarlık edilemez ilkeleri. **Önemli:** Spec Kit, constitution'daki "MUST" ilkelerini otomatik olarak *CRITICAL* sayar; `analyze` bunları ihlal eden spec/plan/görevi hata olarak işaretler.
- `.specify/templates/` — spec/plan/görev şablonları (özelleştirilebilir).
- Her özellik için `specs/<N>-<özellik-adı>/` altında `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `tasks.md`, `checklists/`.
- Her özellik kendi numaralı **Git dalında** yaşar; dal değiştirerek özellikler arası geçersin.

Bu ikilinin birlikte gücü: Spec Kit "ne/nasıl/adımlar" dokümanlarını ve kapılarını verir; Claude Code bu dokümanları okuyup kodu üretir ve subagent/hook'larla *kapsam sınırlarını zorlar*.

---

## 2. Brownfield uyarısı (önce oku)

SDD araçları en güçlü olduğu yer sıfırdan projeler. Senin durumun mevcut bir .NET uygulaması, yani **brownfield** — bu en zorlu senaryo. Bunu güvenli kılmanın yolu:
- Spec Kit'i mevcut repoda başlat (`specify init .`), boş bir klasörde değil.
- **Küçük kapsamlı spec'ler** yaz: tek bir US, net "kapsam içi / kapsam dışı" sınırlarıyla. Büyük "big-bang" spec'lerden kaçın.
- Constitution'a mevcut mimariyi koruma kurallarını koy (Faz 2).
- Kapsam denetçisi subagent'ını (Faz 3) ilk fazdan sonra mutlaka ekle — brownfield'da asıl risk, ajanın "yardımsever" şekilde alakasız yerlere dokunmasıdır.
- C#/.NET, ajanlar için Python'dan belirgin daha zor bir dil (bir ölçümde Python görevlerinin ~%70'i çözülürken C# görevlerinin ~%40'ı). Bu yüzden insan onay kapıları senin için lüks değil, zorunluluk.

---

## Faz 0 — Ortam kurulumu

**Amaç:** Araçları kur, mevcut repoda iskeleti ayağa kaldır.

1. **uv** kur (Spec Kit'in Python paket yöneticisi). Windows PowerShell:
   `powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"`
2. **Claude Code** kur (Node.js gerekir). `npm install -g @anthropic-ai/claude-code` — güncel adımlar için: `code.claude.com/docs`. VS Code eklentin zaten varsa onu da kullanabilirsin.
3. **Test reposu/dalı:** Üretim repon yerine önce bir *kopya repo* veya izole bir feature dalında çalış. İlk denemeleri asla ana dalda yapma.
4. **Spec Kit'i başlat** (repo kökünde):
   `uvx --from git+https://github.com/github/spec-kit.git specify init . --ai claude`
   (Ajan seçimi sorulursa Claude Code'u seç. Kalıcı kurulum istersen: `uv tool install specify-cli --from git+https://github.com/github/spec-kit.git`)
5. **CLAUDE.md tohumla:** Claude Code'u repoda aç ve `/init` çalıştır — kod tabanını tarayıp ilk CLAUDE.md taslağını çıkarır.

**Bitti sayılır:** `.specify/` ve `.claude/` klasörleri oluştu, `/speckit.*` komutları Claude Code sohbetinde görünüyor.

---

## Faz 1 — Yürüyen iskelet (walking skeleton)

**Amaç:** Tek bir küçük task'ı, tüm onay kapılarından geçirerek uçtan uca çalıştırmak. Burada hedef "mükemmel kod" değil, **akışın oturması**.

1. Küçük, düşük riskli bir task seç (tercihen *Feature Enhancement* — mevcut bir davranışa küçük ekleme). Tek başına bir dal aç.
2. Sırayla çalıştır ve **her komuttan sonra dur, üretilen dosyayı oku, onayla**:
   `/speckit.specify` → `spec.md`'yi oku, beklentine uyuyor mu?
   `/speckit.clarify` → belirsizlikleri kapat.
   `/speckit.plan` → `plan.md`'yi oku.
   `/speckit.tasks` → `tasks.md`'yi oku (ajanın *ne* yazacağının aynası — en kritik kontrol noktası).
   `/speckit.analyze` → tutarlılık/kapsam boşluğu raporunu oku, gerekirse düzelt.
   `/speckit.implement` → kodu üret.
3. **Her fazdan sonra Git checkpoint** at (`git add -A && git commit`). Böylece beğenmediğin bir aşamayı geri alıp spec'i düzeltip yeniden üretebilirsin.
4. `git diff` ile üretilen değişikliği elle incele.

**Bilinen tuzak:** Aşırı hevesli modeller (Claude Sonnet dahil) bazen teknik detayı (kütüphane, renk, boyut) yanlışlıkla *spec'e* sızdırır. Spec'te teknik detay görürsen, "bunları spec'ten çıkar, plan'a taşı" de.

**Bitti sayılır:** Bir task tipi, senin onayınla baştan sona geçti ve push edilebilir bir diff çıktı.

---

## Faz 2 — Kuralları kodla: constitution + CLAUDE.md

**Amaç:** Notlarındaki tüm kısıtları makinenin uyacağı kurallara çevirmek. (Şablonlar: Ek A ve Ek D.)

1. `/speckit.constitution` ile constitution'ı doldur. Kısıtlarını **MUST / MUST NOT** olarak yaz (MUST = CRITICAL kapı):
   - Mevcut mimari korunur; US dışı business logic değiştirilemez.
   - Kapsam sınırı: yalnızca spec'te "kapsam içi" denen dosya/davranışlara dokunulur.
   - Tek sorumluluk (single responsibility) — fonksiyon uzun olabilir, ama tek bir iş yapar.
   - Önce doğruluk, sonra performans (iki ayrı aşama).
   - DB erişiminde eşzamanlılık/race condition kontrol edilir; **döngü içinde DB çağrısı yasak** (listeyi toplu çek, bellekte işle).
   - Her paket/dosya/metot, *ne* yaptığını anlatan kısa bir yorumla açıklanır (soyutlama yorumlara da yansır).
2. `CLAUDE.md`'yi sadeleştir ve doldur: .NET sürümü, build/run/test komutları, mimari haritası, kod stili, "always/never" kuralları. Kısa tut — Claude bir oturumda öğrenebileceği şeyleri buraya yazma; sadece kendi başına keşfedemeyeceği kuralları yaz.

**Bitti sayılır:** Faz 1'deki task'ı sildikten sonra tekrar ürettiğinde, kod kurallara (yorumlar, kapsam, DB pattern'i) belirgin biçimde daha uyumlu çıkıyor.

---

## Faz 3 — Kapsam denetçisi (senin "!!" sorunun)

**Amaç:** "Ajan sadece yetkili olduğu yere mi dokundu?" sorusunu otomatikleştirmek. (Şablon: Ek C.)

1. `.claude/agents/scope-reviewer.md` adında **salt-okunur** bir subagent tanımla. Araçları yalnızca okuma + `git diff`; Edit/Write **yok**. Görevi:
   - `git diff`'i ilgili spec'in "kapsam içi" listesiyle karşılaştır.
   - Kapsam dışı dosya/değişiklikleri işaretle.
   - Atlanan eşzamanlılık/race condition, döngü içi DB çağrısı, kapsam dışı business logic değişikliği gibi kalıpları ara.
   - Yapısal bir rapor üret (severity'li). Hiçbir şeyi değiştirme.
2. **Hook ekle** (`settings.json`): düzenleme sonrası `dotnet format` (post-tool formatter); riskli komutlar için ön kapı.
3. **Git checkpoint disiplinini** kalıcılaştır: her faz commit'i + denetçi raporu yeşil olmadan onay verme.

**Bitti sayılır:** Bilerek kapsam dışına çıkan bir spec verdiğinde, denetçi bunu sen fark etmeden yakalıyor.

---

## Faz 4 — Çok tipli task desteği: tip = analiz merceği

**Amaç:** Pipeline'ın her task tipiyle çalışması. Kilit fikir şu: iskelet sabit kalır (`specify → plan → tasks → implement` + onay kapıları); `type` alanı kozmetik bir şablon seçimi değildir — pipeline'da **üç şeyi birden** parametreler:

1. **Analiz merceği** — `specify`/`clarify`'ın *hangi soruları sormak zorunda olduğu* (ve hangilerini atlayacağı).
2. **"Done" tanımı** — kabul kriterinin biçimi; çünkü her tipte "doğru" farklı bir şey demektir.
3. **Denetçi kontrol listesi** — kapsam denetçisinin (Ek C) tipe göre neye bakacağı.

Tipleri üç aileye ayırmak işi netleştirir:
- **Davranış üreten:** Feature Implementation, Feature Enhancement, Bug Fixing — analiz *yeni/değişen* davranışı tanımlar.
- **Davranış koruyan:** Refactoring, Performance Optimization — analiz tersine döner: yeni davranış tanımlamaz, **değişmemesi gereken davranışı (invariant) sabitler.** Senin migration örneğin tam burada: "ne yapmalı" zaten cevaplı; iş analizi davranışsal sözleşmeyi (girdi/çıktı, public API, yan etkiler) dondurur ve "fırsatçı iyileştirme yok" der.
- **Kod üretmeyen:** Code Understanding — `implement` çalışmaz; çıktı analiz/özettir.

Analiz merceğinin ve "done"un tipe göre değişimi:

| Tip | Analiz neyi merkeze alır | Done (kabul) |
|---|---|---|
| Feature Implementation | Sıfırdan davranış: EARS kriterleri, veri modeli, edge case'ler | Yeni davranış tanımlandığı gibi çalışır |
| Feature Enhancement | Mevcut davranışın öncesi→sonrası deltası + korunacaklar | Delta uygulandı, komşu davranış bozulmadı |
| Bug Fixing | Repro + beklenen vs gerçek + kök neden + regresyon sınırı | Hata düzeldi, başka hiçbir şey değişmedi |
| Refactoring | Değişmeyecek dış davranış (invariant) + yapısal hedef; yeni gereksinim YOK | Dış davranış birebir aynı, iç yapı iyileşti |
| Performance Optimization | Baseline ölçüm + sayısal hedef + davranış invariant'ı | Hedef metrik tuttu, davranış aynı |
| Code Understanding | Hangi soru cevaplanacak; çıktı kod değil | Soru yeterince yanıtlandı |
| Test Generation | Mevcut davranışın/spec'in kapsanması | Kapsama hedefi + testlerin doğruluğu |

**Mekanik olarak nasıl bağlanır:**
1. US'nin `type` alanı → **aile** (3) → tek bir composite-router `spec-template.md` içinde ilgili aile bloğunu seçer. (Tip başına 6-7 ayrı şablon — Ek E'nin ilk tasarımı — bilinçli olarak BUNUNLA değiştirildi; aşağıdaki "Güncelleme" notuna bak.)
2. `type` → constitution'daki **tip-koşullu MUST'ları** etkinleştirir (Ek A, Madde 8) — örn. davranış-koruyan tiplerde "dış davranış birebir korunur"; feature'da bu kural devreye girmez.
3. `type` → denetçinin kontrol listesini değiştirir (Ek C) — refactor/perf'te "diff gözlemlenebilir bir davranışı değiştirdi mi?", feature'da "kapsam taşması var mı?".

**Güncelleme (uygulanan tasarım) — 3-aile composite router:** Faz 4'ü, Ek E'deki gibi *tip başına 6-7 ayrı şablon* olarak değil, **tek bir `spec-template.md` içinde `Type` alanı + router** olarak kurduk. Router `type → aile` eşler ve üç aile bloğundan birini tutup diğerlerini sildirir: **Davranış-üreten** (dolu), **Davranış-koruyan** (dolu, invariant-merkezli), **Kod-üretmeyen** (stub). Gerekçe: spec'in ağırlık merkezi *tip* düzeyinde değil **aile** düzeyinde değişir; bu, 7 neredeyse-aynı şablonun bakım yükünü 3'e indirir; granular `type` yine de Madde VII + denetçi merceğini besler (template aileye *kendisi* indirger).
- **Yapıldı:** granular `Type` alanı, composite router, 3 aile bloğu, her ailede açık `Scope (In/Out)` + opsiyonel `Constraints / Imposed Decisions` bölümü, "boundary vs implementation-HOW" carve-out'lu HARD RULE. (Madde VII zaten Faz 2'de, denetçi tip merceği zaten Faz 3'te hazırdı.)
- **Ertelendi:** tip başına ayrı şablon granülaritesi (gözlemlenen bir boşluk gerektirmedikçe açılmayacak); `Type = Code Understanding`'de `implement`'i otomatik atlama (orchestration wiring'i, salt not değil); tanınmayan/desteklenmeyen `Type` için router guard'ı; `specify`'ın tipi *sorması* (şimdilik konvansiyon: tip input'ta belirtilir). Test Generation zaten Faz 6.
- **Kalan "bitti sayılır":** üç aileden birer tipi pipeline'dan geçiren doğrulama koşusu — özellikle bir davranış-koruyan tip (denetçi bir davranış değişikliğini yakalıyor mu?).
- İlgili dosyalar: `.specify/templates/spec-template.md`, `docs/spec-template-aile-bloklari.md`.

**Dürüst gerilim (v1 ile):** Davranış-koruyan tipler (refactor, migration, perf) tam da eşdeğerliği *gözle doğrulamanın en zor olduğu* tiplerdir — test olmadan "davranış aynı kaldı" iddiası denetçinin statik analizine yaslanır. v1'de bunu kabul ediyoruz; ama testin sisteme doğal olarak ilk gireceği yer de burasıdır: **karakterizasyon testleri** (geçişten önce mevcut davranışı dondurup sonra aynısını doğrulayan testler) migration için ucuz bir sigortadır. Faz 6'da "ilk test buraya" diye işaretle.

**Bitti sayılır:** Üç farklı aileden en az birer tip (örn. enhancement, refactor/migration, bug fix) aynı pipeline'dan, tipine uygun *analiz merceğiyle* geçiyor; refactor/migration'da denetçi davranış değişikliğini yakalıyor.

---

## Faz 5 — Entegrasyonlar (omurga oturduktan sonra)

**Amaç:** Manuel kopyala-yapıştır adımlarını otomatikleştirmek.

- **Azure DevOps (MCP):** Bir Azure DevOps MCP sunucusunu `.mcp.json`'a ekleyip US'leri otomatik çekmek ve dal/PR açmak. (Güncel resmi MCP için arama yap; bu adım önceliğin değildi.)
- **Mentor yükseltme (escalation):** Notundaki WhatsApp fikri. Burada kritik olan: ajanın *ne zaman* sana soracağının **spec'inin net olması** (senin de işaretlediğin gibi). Tetikleyicileri açıkça tanımla — örn. "iki US'nin çakıştığı nokta", "mimari karar", "constitution ile çelişki". Önce basit tut: ajan, bu tetikleyicilerde durup yapılandırılmış bir soru üretsin; iletim kanalı (WhatsApp vb.) en sona.
- **Business analist adımı:** Pipeline'da `specify` öncesine oturur — US'yi netleştiren ve "kararlar" bölümünü dolduran rol. İstersen bunu ayrı bir "analyst" persona/subagent olarak modelle.

---

## Faz 6 — Sonraki: test ve doğrulama

v1'de yoktu; olgunlaştıkça ekle:
- Test Generation task tipini aç; spec'ten kabul kriterlerine dayalı testler üret.
- `analyze`'ı implement *sonrası* da koşarak ekstra bir doğrulama turu yap.
- Bir CI kapısı ekleyip "anlık doğrulama" isteğini (feature/performans gereksinimi karşılandı mı?) otomatikleştir.
- O noktada spec-first'ten "spec + test birlikte" rigor seviyesine geçebilirsin.

---

## Maliyet / token notları

- En ağır token tüketimi `implement` ve büyük bağlam yüklemelerinde olur. CLAUDE.md'yi kısa tutmak (her oturum bağlama girdiği için) doğrudan tasarruftur.
- Subagent'lar bağlam yönetimi açısından *tasarruf* sağlar: işlerini izole bağlamda yapıp ana sohbete sadece özet döner.
- Pratik tasarruf: planlama/inceleme gibi muhakeme ağır adımlarda güçlü model, mekanik `implement` adımında daha ucuz model düşünebilirsin (model seçimini açık kararlar listesine ekledim).
- Spec yazmanın da bir maliyeti var: küçük bir düzeltme için tam SDD turu israf olabilir. Kural: "ajan gereksinimi benim kastettiğimden farklı yorumlarsa canım sıkılır mı?" — evetse spec yaz, hayırsa doğrudan promptla.

---

## Senin vereceğin açık kararlar

- Spec rigor seviyesi: spec-first mi, spec-anchored mı? (v1 için spec-first öneririm.)
- Dallanma stratejisi: özellik başına dal + PR akışı nasıl olacak?
- Model seçimi: hangi adımda hangi model?
- Mentor escalation tetikleyicilerinin tam spec'i.
- Business analist adımının pipeline'daki tam yeri ve çıktısı.
- Dosya düzeni kuralları (notundaki açık soru) — CLAUDE.md ve constitution'a yazılacak.

---

## Ek A — Constitution iskeleti (`.specify/memory/constitution.md`)

```markdown
# Proje Anayasası

## Madde 1 — Mimari koruma (MUST)
- Mevcut mimari ve katman sınırları korunur.
- US'de tanımlanmamış business logic DEĞİŞTİRİLMEZ (MUST NOT).

## Madde 2 — Kapsam sınırı (MUST)
- Yalnızca spec'in "Kapsam içi" bölümünde listelenen dosya/davranışlara dokunulur.
- Kapsam dışı bir değişiklik gerekiyorsa: dur ve sor (escalation).

## Madde 3 — Tek sorumluluk (MUST)
- Her fonksiyon tek bir sorumluluk taşır. Uzunluk değil, sorumluluk sayısı önemlidir.

## Madde 4 — Önce doğruluk, sonra performans (MUST)
- Adım 1: requirement'ı karşılayan, mimariyi bozmayan çalışır sürüm.
- Adım 2: ölçülebilir performans iyileştirmesi.

## Madde 5 — Veri erişimi (MUST)
- Döngü içinde DB çağrısı YAPILMAZ; liste toplu çekilir, bellekte işlenir.
- Read/write yollarında eşzamanlılık/race condition açıkça ele alınır.

## Madde 6 — Soyutlama yorumlara yansır (MUST)
- Her paket/dosya/metot, "ne yaptığını" anlatan kısa bir yorumla açıklanır.

## Madde 7 — .NET/C# konvansiyonları (MUST)
- [proje stil rehberin buraya]

## Madde 8 — Tip-koşullu davranış koruma (MUST)
- type ∈ {Refactoring, Performance Optimization} ise: gözlemlenebilir dış davranış
  (girdi/çıktı, public API sözleşmesi, yan etkiler, veri şekilleri) BİREBİR korunur.
- Bu tiplerde yeni fonksiyonel gereksinim eklenmez; "fırsatçı iyileştirme" YASAK (MUST NOT).
- type = Bug Fixing ise: yalnızca hatalı davranış değişir; komşu davranışlar korunur.
- type = Code Understanding ise: kod ÜRETİLMEZ (implement çalıştırılmaz); çıktı analizdir.
```

## Ek B — Azure user story girdi formatı

```markdown
# US-[id]: [başlık]

## Tip
[Feature Implementation | Feature Enhancement | Bug Fixing | Refactoring |
 Performance Optimization | Code Understanding | Test Generation]

## Bağlam / teori
Bu özellik ne işe yarıyor, hangi problemi çözüyor? (nasıl'a girmeden)

## Kararlar (requirement analizi)
Kritik kararlar: hangi algoritma, TTL, sınır değerler, vb.

## Gereksinimler (EARS tarzı)
- WHEN [koşul], THE SYSTEM SHALL [davranış].
- ...

## Kapsam
- İçi: [dokunulabilecek dosyalar/modüller]
- Dışı: [kesinlikle dokunulmayacaklar]

## Fonksiyonel olmayan
- Performans / güvenlik / eşzamanlılık beklentileri.

## Definition of done
- [ ] ...
```

## Ek C — Kapsam denetçisi subagent (`.claude/agents/scope-reviewer.md`)

```markdown
---
name: scope-reviewer
description: Üretilen diff'in spec kapsamıyla uyumunu denetler. Salt-okunur.
tools: Read, Grep, Bash(git diff:*)
---

Sen salt-okunur bir kod inceleyicisin. HİÇBİR dosyayı değiştirmezsin.

Görevin:
1. `git diff`'i al ve ilgili spec'in "Kapsam" bölümüyle karşılaştır.
2. HER tipte işaretle:
   - Kapsam dışı dosya/değişiklikler.
   - US dışı business logic değişiklikleri.
   - Döngü içi DB çağrısı.
   - Ele alınmamış race condition / eşzamanlılık (read/write yolları).
   - Anayasa (constitution) MUST ihlalleri.
3. US'nin `type` alanına göre EK olarak şunlara bak:
   - Refactoring / Performance Optimization: diff gözlemlenebilir bir dış davranışı
     veya public API sözleşmesini değiştirdi mi? Yeni fonksiyonel davranış sızdı mı?
     (Her ikisi de Critical.) Performans'ta ayrıca: değişiklik gerçekten hedef metrik
     yoluna mı dokunuyor, yoksa alakasız yeri mi değiştirdi?
   - Feature Enhancement / Bug Fixing: spec'in "korunacaklar" listesindeki davranışlara
     dokunulmuş mu? Düzeltme/değişiklik niyetlenen kapsamın dışına taşmış mı?
   - Feature Implementation: yeni kod mevcut mimariye uyuyor mu, var olanı kopyalayıp
     çoğaltmış (duplication) mı?
   - Code Understanding: hiç kod değişikliği OLMAMALI; varsa Critical.
4. Severity'li (Critical/High/Medium/Low) yapılandırılmış bir rapor üret.
5. Sonunda net bir karar ver: ONAY / DÜZELTME GEREKLİ.
```

## Ek D — CLAUDE.md iskeleti

```markdown
# [Proje adı]

## Genel bakış
[1-2 cümle: ne yapan bir sistem]

## Yığın
- .NET [sürüm], [framework], [DB]

## Komutlar
- Build: ...
- Run: ...
- Test: ...

## Mimari haritası
- [katman/klasör → sorumluluk]

## Kod stili & kurallar
- Always: ...
- Never: ...

## Referanslar
- Anayasa: .specify/memory/constitution.md (MUST kuralları orada)
```

---

## Ek E — Tipe özel spec şablonları

> **GÜNCELLEME (uygulanan tasarım):** Bu ek **ilk tasarımdır** — *tip başına ayrı şablon*. Faz 4'te bunun yerine **tek `spec-template.md` + `Type` router** ile **3 aile** kurduk (bkz. Faz 4 "Güncelleme" notu ve `docs/spec-template-aile-bloklari.md`). Aşağıdaki tip-bazlı içerik, ailelere konsolide edilmiş haliyle template'te yaşıyor (A=üreten, B=koruyan, C=kod-üretmeyen); bu ek artık **vurgu/içerik referansı** olarak korunuyor, doğrudan oluşturulacak dosya listesi değil.

Bu şablonlar `.specify/templates/` altında tip başına tutulur; US'nin `type` alanı hangisinin yükleneceğini belirler. Ortak iskelet (başlık, bağlam, kapsam, done) sabit kalır; **vurgu** tipe göre kayar. Davranış-koruyan tiplerde (E.4, E.5) "fonksiyonel gereksinim" bölümünün neredeyse boş, "invariant" bölümünün ise kalp olduğuna dikkat et.

### E.1 Feature Implementation (sıfırdan)

```markdown
# Spec — Feature Implementation
## Davranış (EARS)
- WHEN [koşul], THE SYSTEM SHALL [yeni davranış].
## Veri modeli
- Entity / alanlar / ilişkiler
## Edge case'ler
- ...
## Kapsam: içi / dışı
## Done: yeni davranış tanımlandığı gibi çalışır
```

### E.2 Feature Enhancement

```markdown
# Spec — Feature Enhancement
## Mevcut davranış (öncesi)
- Şu an ne oluyor?
## Hedef davranış (sonrası) — DELTA
- Tam olarak ne değişecek? (sadece fark)
## Korunacak davranışlar (MUST aynı kalsın)
- Bu enhancement'ın dokunmaması gereken komşu davranışlar
## Kapsam: içi / dışı
## Done: delta uygulandı, korunacaklar bozulmadı
```

### E.3 Bug Fixing

```markdown
# Spec — Bug Fixing
## Tekrar üretim (repro)
- Adımlar / koşullar
## Beklenen vs gerçek
- Beklenen (asıl niyet): ...
- Gerçek (hatalı): ...
## Kök neden (biliniyorsa / araştırılacaksa)
## Regresyon sınırı (MUST)
- Yalnızca bu davranış değişir; şunlar aynı kalır: ...
## Kapsam: içi / dışı
## Done: hata düzeldi, başka hiçbir davranış değişmedi
```

### E.4 Refactoring / Migration

```markdown
# Spec — Refactoring / Migration
## Değişmeyecek dış davranış (INVARIANT — MUST)
- Davranışsal sözleşme: girdi/çıktı, public API, yan etkiler, veri şekilleri
## Yapısal hedef
- Ne iyileşecek? (örn. X kütüphanesi → Y; katman ayrımı)
## Eski → yeni eşleme (migration ise)
- Eski yapı/teknoloji  →  yeni karşılığı
## YASAK (MUST NOT)
- Yeni fonksiyonel gereksinim; fırsatçı iyileştirme; davranış değişikliği
## Kapsam: içi / dışı
## Done: dış davranış birebir aynı, iç yapı/teknoloji değişti
```

### E.5 Performance Optimization

```markdown
# Spec — Performance Optimization
## Baseline (mevcut ölçüm)
- Metrik + bugünkü değer (örn. p95 latency, sorgu sayısı)
## Hedef (sayısal)
- Aynı metrik için ulaşılacak değer
## Davranış invariant'ı (MUST)
- Çıktı/sonuç birebir aynı kalır; yalnızca hız/kaynak değişir
## Yaklaşım (varsa kısıt)
- örn. döngü içi DB → toplu çek + bellekte işle
## Kapsam: içi / dışı
## Done: hedef metrik tuttu, davranış değişmedi
```

### E.6 Code Understanding (kod ÜRETİLMEZ)

```markdown
# Spec — Code Understanding
## Cevaplanacak soru(lar)
- ...
## İnceleme kapsamı
- Hangi modüller/dosyalar
## Çıktı biçimi
- Analiz / akış özeti / risk listesi (implement ÇALIŞTIRILMAZ)
## Done: soru yeterli derinlikte yanıtlandı
```

### E.7 Test Generation (v1 dışı, Faz 6)

```markdown
# Spec — Test Generation
## Kapsanacak davranış/spec
- Hangi mevcut davranış test altına alınacak?
## Kapsama hedefi
- örn. kritik yolların %X'i; karakterizasyon testleri
## Çıktı
- Mevcut davranışı donduran testler
## Done: kapsama hedefi tuttu, testler yeşil ve doğru
```
