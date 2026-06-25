# Mona DRP — Yerel Geliştirme Ortamı Kurulumu

Bu rehber, projeyi sıfırdan açan herkesin tek komutla çalışır hale gelmesini sağlar.  
Önceden PostgreSQL kurulu olmasına gerek yoktur — Docker her şeyi halleder.

---

## Ön Koşullar

| Araç | Minimum Sürüm | Kontrol |
|---|---|---|
| Docker Desktop | 24+ | `docker --version` |
| Docker Compose | 2.x (Docker ile gelir) | `docker compose version` |
| psql (PostgreSQL client) | 14+ | `psql --version` |
| SBT | 1.9+ | `sbt --version` |
| Java | 11 veya 17 | `java -version` |

> **Not:** PostgreSQL'i yerel olarak kurmanıza gerek yok.  
> `setup` scripti Docker aracılığıyla PostgreSQL + PGMQ kurulu imajı başlatır,  
> veritabanını ve tabloları otomatik oluşturur.

---

## Hızlı Başlangıç (Tek Komut)

**Linux / macOS:**

```bash
bash scripts/setup.sh
```

**Windows (PowerShell):**

```powershell
.\scripts\setup.ps1
```

Bu kadar. Script şunları otomatik yapar:
1. `.env.example`'dan `.env` oluşturur (yoksa)
2. Docker konteyneri başlatır → PostgreSQL + veritabanı (`mona_drp`) + kullanıcı (`mona`) otomatik oluşur
3. PostgreSQL hazır olana dek bekler
4. 6 migration dosyasını sırayla çalıştırır (16 tablo, 5 PGMQ kuyruğu)

Ardından `sbt run` ile uygulamayı başlatın.

---

## Manuel Adımlar (Alternatif)

Adımları tek tek çalıştırmak isterseniz:

### Adım 1 — Ortam Dosyasını Hazırla

```bash
cp .env.example .env
```

`.env` dosyasını açıp `DB_PASSWORD` değerini değiştirin (isterseniz varsayılan değerle de çalışır).  
`.env` dosyası `.gitignore` tarafından hariç tutulur; asla commit'lemeyin.

---

## Adım 2 — Veritabanı Konteynerini Başlat

```bash
docker compose up -d
```

Bu komut `pgmq/pg18-pgmq:v1` imajını indirir ve PostgreSQL'i **55432** portunda başlatır.  
İlk indirme ~500 MB sürebilir.

Hazır olduğunu kontrol edin:

```bash
docker compose ps
# Beklenen: mona-drp-postgres   Up (healthy)
```

---

## Adım 3 — Migrationları Çalıştır

**Linux / macOS:**

```bash
bash scripts/migrate_drp_up.sh
```

**Windows (PowerShell):**

```powershell
.\scripts\migrate_drp_up.ps1
```

Her iki script de `.env` dosyasını okur ve V001'den V006'ya kadar 6 migration dosyasını sırayla çalıştırır.  
Bir hata oluşursa script durur ve hangi migration'da hata aldığını gösterir.

Beklenen çıktı:

```
=== Mona DRP Migration UP ===
Hedef: localhost:55432/mona_drp

-> V001__asset_layer_up.sql
   OK
-> V002__discovery_layer_up.sql
   OK
...
-> V006__pgmq_queues_up.sql
   OK

Migration tamamlandi.
```

---

## Adım 4 — Şemayı Doğrula

```bash
# .env'den değerleri alarak çalıştır:
source .env   # Linux/Mac
psql "postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}" \
     -f scripts/check_drp_schema.sql
```

**Windows (PowerShell):**

```powershell
# .env yükle
Get-Content .env | ForEach-Object {
  if ($_ -match '^([^=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
psql "postgresql://${env:DB_USER}:${env:DB_PASSWORD}@${env:DB_HOST}:${env:DB_PORT}/${env:DB_NAME}" `
     -f scripts/check_drp_schema.sql
```

Beklenen sonuç:
- **16 tablo** (entities, assets, candidates, …, blob_storage)
- **7 trigger** (updated_at tetikleyicileri)
- **5 PGMQ kuyruğu** (candidate_validation_queue, crawl_queue, …)
- Tüm satırlarda `OK` statüsü

---

## Adım 5 — Uygulamayı Başlat

`conf/application.conf` içindeki veritabanı bağlantısı `.env` değerlerini okuyacak şekilde ayarlanmıştır.  
Uygulamayı başlatmak için:

```bash
sbt run
```

Tarayıcıda `http://localhost:9000` adresini açın.

---

## Rollback (Tabloları Sil)

Migration'ları geri almak için:

```bash
# Linux/Mac
bash scripts/migrate_drp_down.sh

# Windows
.\scripts\migrate_drp_down.ps1
```

Script, devam etmeden önce `evet` onayı ister.

---

## Konteyneri Durdur / Sil

```bash
# Sadece durdur (veriler korunur)
docker compose stop

# Tamamen sil (veriler de silinir)
docker compose down -v
```

---

## Sorun Giderme

| Sorun | Çözüm |
|---|---|
| `psql: could not connect to server` | `docker compose ps` ile konteynerin `healthy` olduğunu kontrol edin |
| `FATAL: password authentication failed` | `.env` içindeki `DB_PASSWORD` değerinin `docker-compose.yml`'deki ile aynı olduğunu doğrulayın |
| Port 55432 meşgul | `.env` ve `docker-compose.yml` içindeki `DB_PORT` değerini boş bir porta değiştirin |
| `pgmq.create: ERROR` | Docker imajının `pgmq/pg18-pgmq:v1` olduğundan emin olun (standart postgres imajı çalışmaz) |
| Scala derleme hatası | `sbt clean compile` çalıştırın |
