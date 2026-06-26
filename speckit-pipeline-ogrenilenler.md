# Spec Kit + Claude Code Pipeline — Öğrenilenler

Bu doküman, agentic SDD (Spec-Driven Development) pipeline'ını Faz 1 (walking skeleton: PhishProne rapor filtreleme/sıralama) üzerinde uçtan uca çalıştırırken öğrendiğimiz kritik bilgileri toplar. Pratik referans amaçlıdır.

---

## 0. Büyük resim — iki ayrı dizin

İlk ve en önemli netleştirme: **iki farklı dizin var ve karıştırılmamalı.**

| Dizin | Ne tutar | Örnek içerik |
|---|---|---|
| **`.claude/skills/`** | **Komutların kendisi** (skill'ler). `/speckit-*` komutları **burada** tanımlı. | `.claude/skills/speckit-specify/SKILL.md`, `speckit-plan/`, `speckit-tasks/` ... |
| **`.specify/`** | Komutların kullandığı **destek varlıkları** | `templates/` (spec/plan/tasks şablonları), `scripts/powershell/` (yardımcı scriptler), `memory/constitution.md` (anayasa), `feature.json` (aktif feature işaretçisi), `extensions.yml` (hook'lar) |

> Komutlar **`.claude/skills/` altında skill** olarak tanımlı. `.specify/` ise bu komutların okuduğu şablon/script/anayasa/işaretçi dosyalarını tutar.

### Skill nedir?
Skill = **gerektiğinde yüklenen, bir işin nasıl yapılacağını anlatan talimat dosyası** (`SKILL.md`). Her zaman context'te durmaz; sen `/speckit-plan` yazınca o talimat context'e yüklenir ve **ajan (Claude) o reçeteyi uygular**. Yani "komutu çalıştırmak" = ajanın o skill'deki adımları işletmesi. Skill otomatik çalışan bir program değildir.

**Pratik sonuç:** Skill'ler oturum başında yüklenir. Pipeline'ı `specify init` ile yeni kurduysan, `/speckit-*` komutlarının görünmesi için Claude Code'u **yeniden başlatman** gerekir.

---

## 1. Akışın tamamı (tek bakış)

```
constitution (bir kez) → specify → clarify → [checklist] → plan → tasks → analyze → implement
                            ▲          ▲          ▲          ▲        ▲         ▲          ▲
                          spec.md   spec.md    ek         plan.md  tasks.md  rapor      kod
                          üretir    güncel.    checklist  +artefakt          (salt-     +tasks[X]
                                                                              okunur)
```

- **Dosya değiştirenler:** `specify`, `clarify`, `plan`, `tasks`, `implement`
- **Salt-okunur (hiçbir dosyayı değiştirmez):** `clarify` soruları (cevap girilene kadar), `checklist` üretimi hariç → **`analyze` tamamen salt-okunur**. Bunlar senin onay kapılarının destekçileridir.
- **Her adımdan sonra:** oku → onayla → `git commit` (checkpoint). İlerleme kararı **insanda**.

---

## 2. Komut komut — ne yapar

### `constitution` (`/speckit-constitution`)
Projenin pazarlık edilemez **MUST kurallarını** `.specify/memory/constitution.md`'ye yazar. `analyze` bu MUST'ları **CRITICAL** sayar; ihlal eden spec/plan/task'ı hata işaretler.W

### `specify` (`/speckit-specify`)
- **Girdi:** doğal dilde bir user story / feature açıklaması (komutun arkasına, aynı satıra yazılır → `$ARGUMENTS`).
- **Yapar:** açıklamayı `.specify/templates/spec-template.md`'ye göre **yapılandırılmış bir dokümana** çevirir. Sadece **"NE" beklendiğini** yazar; **"NASIL" yoktur** (sınıf/dosya/kütüphane adı spec'e sızmamalı — bilinen tuzak).
- **Üretir:**
  - `specs/NNN-<kısa-ad>/spec.md` — user story'ler (öncelikli), kabul senaryoları (Given/When/Then), fonksiyonel gereksinimler (FR), başarı kriterleri (SC), edge case'ler, varsayımlar.
  - `specs/NNN-<kısa-ad>/checklists/requirements.md` — otomatik **kalite checklist'i** (spec yeterince iyi mi?).
  - `.specify/feature.json` — "aktif feature dizini" işaretçisi (sonraki komutlar bununla feature'ı bulur).
- **Önemli kavram:** `spec.md` = **alacağımız çıktının sözleşmesidir (contract).** Implement bunun maddelerine dayanır; sen de üretilen kodu bu maddelerle denetlersin.
- (Belirsizlik çok kritikse spec içine en fazla 3 `[NEEDS CLARIFICATION]` işareti koyabilir.)

### `clarify` (`/speckit-clarify`)
- **Girdi yok** (spec'i `feature.json`'dan bulur).
- **Yapar:** `spec.md`'yi belirsizlik taramasından geçirir, **net olmayan en fazla 5 noktayı** bulur, sana **çoktan seçmeli** olarak (önerisini en üstte vererek) sorar.
- **Senin işin:** soruları **sen cevaplarsın** (bunlar senin kararların; ajan senin yerine seçmez).
- **Üretir/değiştirir:** cevapları `spec.md`'ye işler → `## Clarifications` bölümü + ilgili FR/SC/Assumptions maddeleri güncellenir; çelişen eski varsayımlar **karara** çevrilip değiştirilir. Sonra kalite checklist'ini yeniden doğrular.
> Pratik ipucu: Varsayım zaten istediğin gibiyse o seçeneği seç → varsayımı "karar"a terfi ettirirsin.

### `checklist` (`/speckit-checklist`) 
- **Opsiyonel** bir kalite komutu. Gereksinimlerin tamlığını/netliğini/tutarlılığını doğrulamak için **özel checklist'ler** üretir (örn. UX, güvenlik, test odaklı).
- **Karışmasın:** `specify`'ın otomatik ürettiği `checklists/requirements.md` ayrı şeydir (o her zaman oluşur). `/speckit-checklist` bunun **üstüne, talep ettiğin konuda ek** checklist üretir.
- Biz çalıştırmadık çünkü tek, küçük ve net bir enhancement'tı; ek checklist'e ihtiyaç olmadı. Büyük/riskli feature'larda faydalıdır.

### `plan` (`/speckit-plan`)
- `.specify/templates/plan-template.md`'ye göre `plan.md` üretir **ve** "Phases" bölümünde tanımlı ek dokümanları çıkarır. Bunlar planı **farklı çerçevelerden gösteren, farklı soyutlamalardaki görünümlerdir:**

  | Dosya | Hangi çerçeve | İçerik |
  |---|---|---|
  | **plan.md** | Ana strateji | Teknik bağlam (dil, bağımlılık, depolama), **Constitution Check** (kapılar geçti mi), **Structure Decision** (hangi dosyalar, hangi yaklaşım), özet |
  | **research.md** (Phase 0) | **Karar günlüğü** | Her teknik seçim için *Decision / Rationale / Alternatives*. "Neden böyle yaptık, başka ne düşünüldü." (Örn. D3: harf-katlama neden `OrdinalIgnoreCase`.) |
  | **data-model.md** (Phase 1) | **Veri görünümü** | Hangi entity/alan/ilişki değişiyor; doğrulama kuralları. (Örn. `PhishProneReportFilters`'a 2 alan, enum'a `Name`.) |
  | **contracts/** (Phase 1) | **Dış arayüz görünümü** | API'nin dışa açtığı istek/yanıt sözleşmesi (JSON şekli, davranış kuralları, geriye dönük uyumluluk). *Proje dışa arayüz açmıyorsa atlanır.* |
  | **quickstart.md** (Phase 1) | **Doğrulama görünümü** | "Bu feature çalışıyor mu?" — çalıştırılabilir, adım adım test senaryoları. (Bizde 8 senaryo.) |

- **Atlanan adım:** plan'ın Phase 1'inde bir de "agent context update" var (CLAUDE.md'deki `<!-- SPECKIT START/END -->` markerlarını güncelleme). Bizim CLAUDE.md elle yazılı ve o markerlar yok; kapı kapı modelinde dosyana otomatik dokunmamak için **bilerek atladık**. (Opsiyonel `after_plan` agent-context hook'unun işi de buydu.)

### `tasks` (`/speckit-tasks`)
- `plan.md` + `spec.md` (+ diğer artefaktlar) → `tasks.md`: **somut, sıralı, numaralı, onay kutulu (`- [ ] T001 ...`) iş listesi.** Her görev: ID + (varsa) `[P]` paralel işareti + `[US#]` story etiketi + **kesin dosya yolu**.
- User story bazında fazlara ayrılır (Setup → Foundational → US1 → US2 → ... → Polish), bağımlılık grafiği ve MVP kapsamı belirtilir.

#### 🔑 plan vs tasks — en kritik netleştirme
İkisi de "nasıl"a değiniyor ama **soyutlama seviyeleri farklı:**

- **plan.md = TASARIM / strateji.** "Çözümün teknik şekli ne, hangi dosyalara hangi yaklaşımla dokunacağız, **neden**?" Düz anlatı (prose). Bir **insan onaylamak için** okur. Sıralı, işaretlenebilir bir yapılacaklar listesi **değildir**. ("ApplyTableFilters ekleyeceğiz" der, ama tasarım cümlesi olarak.)
- **tasks.md = İCRA / yapılacaklar listesi.** "Tam olarak hangi sırayla, hangi dosyada, hangi atomik adımı atacağız?" Numaralı, bağımlılık sıralı, **kutu kutu işaretlenen** liste. Bir **ajan satır satır uygular** ve bitince `[X]` işaretler.

> Analoji: **plan = mimari proje (blueprint)**; **tasks = şantiye yapım checklist'i.**
> Evet, plan da kod detayına değer; ama plan "ne inşa edilecek ve niçin"i, tasks "hangi sırayla hangi tuğla konacak"ı verir. Plan yeniden düzenlenebilir metin, tasks mekanik/sıralı/denetlenebilir iş kırılımı.

### `analyze` (`/speckit-analyze`) — tamamen salt-okunur
- `spec.md` ↔ `plan.md` ↔ `tasks.md` **tutarlı mı?** Kapsam boşluğu (gereksiz/eksik görev), çelişki, anayasa (MUST) ihlali var mı kontrol eder.
- **Hiçbir dosyayı değiştirmez.** Severity'li (Critical/High/...) bir bulgu raporu + kapsama tablosu üretir, sonra önerir.
- Bizde işe yaradı: `.ToLower()`'ın Türkçe `I/İ` için riskli olduğunu **HIGH** bulgu olarak yakaladı → implement öncesi düzeltme fırsatı verdi.

### `implement` (`/speckit-implement`)
- Önce checklist durumunu ve ön koşulları kontrol eder. Sonra `tasks.md`'yi faz faz uygular, **biten görevi `[X]` işaretler**, kodu üretir.
- Bizde: 2 dosya, +37/−2 satır, salt eklemeli; `dotnet build` yeşil. (T011 manuel quickstart doğrulaması insanda.)

---

## 3. Destek mekanizmaları (perde arkası)

- **`.specify/scripts/powershell/*.ps1`** — her komutun başında çalışan yardımcılar: `check-prerequisites.ps1`, `setup-plan.ps1`, `setup-tasks.ps1`. Feature yollarını çözer, şablonları kopyalar.
- **`.specify/feature.json`** — aktif feature dizinini işaret eder; komutların git dal adına bağlı kalmadan zincirlenmesini sağlar.
- **`.specify/extensions.yml` (hook'lar)** — `before_*` / `after_*` kancalar. **Opsiyonel** olanlar otomatik koşmaz (biz `after_specify`/`after_plan` agent-context hook'larını atladık); **mandatory** olanlar `EXECUTE_COMMAND` ile koşturulur.
- **Dosya/isim sabit** — `spec.md`, `plan.md`, `research.md`, `tasks.md` vb. isimleri değiştirme; sonraki komutlar bunları **isimle** arar.

---

## 4. Kim ne yapar (insan vs ajan)

| İş | Sahibi |
|---|---|
| Feature açıklaması (specify girdisi) | **İnsan** |
| Clarify sorularını cevaplama | **İnsan** (kararlar) |
| spec/plan/tasks **üretimi**, kod **yazımı** | Ajan |
| Her artefaktı **okuyup onaylama** (kapı) | **İnsan** 🔴 işin kalbi |
| Git checkpoint (her fazda commit) | **İnsan** |
| Sonraki komuta **geçme kararı** | **İnsan** |

**Pipeline'ı güvenli kılan:** insan onay kapıları + her fazda git commit (beğenmediğini geri al, düzelt, yeniden üret). C#/.NET ajanlar için zor bir dil olduğundan bu kapılar lüks değil, zorunluluk.

---

## 5. Pratik gotcha'lar (bizim yaşadıklarımız)

1. **`specify init` "command not found":** `specify` standalone değil; `uv`/`uvx` ile çalışır. Güncel CLI'da bayrak `--ai` değil **`--integration`**, Windows için `--script ps`, dolu repoda `--here --force`.
2. **Komut argümanı geçme:** dropdown'dan komutu **seç** (Enter'a basma) → arkasına **boşluk + açıklama** yaz → sonra Enter. Boş gönderirsen `$ARGUMENTS` boş gelir.
3. **Skill'ler oturum başında yüklenir:** init'ten sonra Claude Code'u yeniden başlat.
4. **Teknik detay spec'e sızmasın:** "DTO/repository/`.ToLower()`" gibi şeyler spec'te değil **plan'da** olmalı.
5. **Kapsam dürüstlüğü:** referans #234 sayesinde kapsamın **2 dosya** olduğunu doğruladık; tasks.md aynı-dosya çakışmasını önleyecek sıralı bağımlılıkla kuruldu (story'ler aynı dosyaları paylaştığı için paralellik yoktu).
6. **VS Code'da uzun komut mesajı küçültülemez** (per-mesaj collapse yok) — argümanı kısa tut.

---

## 6. Bizim örnek (özet)

**Task:** PhishProne raporuna ad/e-posta filtresi + ada göre sıralama (Feature Enhancement).
**Sonuç:** Pipeline çıktısı, daha önce elle yazılmış `feature/234` referansıyla **neredeyse birebir** çıktı. **Tek bilinçli fark:** harf-katlama → pipeline `OrdinalIgnoreCase` (kültür-güvenli), #234 `.ToLower()`. Bu farkı `analyze` kapısı bir doğruluk iyileştirmesi olarak yakalattı.

**Çıkarım:** spec→clarify→plan→tasks→analyze→implement akışı, insan onay kapılarıyla, brownfield bir .NET task'ında gerçek implementasyona denk — yer yer daha iyi — bir sonuç üretti.
