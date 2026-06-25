# PostgreSQL Extension Araştırması — Mona DRP MVP

**Amaç:** MVP'de Kafka, MinIO/S3, Elasticsearch gibi ayrı ayrı sunucular kurup yönetmek yerine, bu işlerin hepsini tek bir PostgreSQL üzerinde yapmak. Daha az kurulum, daha az bakım, daha hızlı demo.

**Önce kafa karışıklığını gidirelim.** Hoca "blob extension'ın içinde Elasticsearch gibi arama olur" dedi, ama bunlar aslında **üç ayrı ihtiyaç** ve üç ayrı çözüm:

1. İşleri sıraya dizip paralel işlemek (mesaj kuyruğu) → **pgmq**
2. Büyük dosyaları saklamak (dosya deposu) → **bytea**
3. O dosyaların içeriğinde kelime aramak (arama motoru) → **tsvector**

> Dosyayı *saklamak* ile içinde *arama yapmak* iki ayrı iştir. Bir dolaba evrak koymak ile o evrakların içinde "akbank" kelimesini aramak gibi. İkisini de Postgres yapabiliyor, ama farklı mekanizmalarla.

---

## 1. pgmq — Mesaj Kuyruğu

**Nedir?** Bir "iş sırası" (kuyruk) sistemidir. Bir taraf işi kuyruğa bırakır (producer), diğer taraf sıradan alıp yapar (consumer). AWS'nin SQS'i veya Kafka bunu yapar — pgmq aynı işi **Postgres'in içinde**, ekstra bir sunucu kurmadan yapar.

**Hangi sorunu çözüyor?** Crawler, analiz, risk scoring gibi işler ağırdır ve birbirini beklememeli. Kullanıcı bir domain ekleyince sistem onu hemen kuyruğa atar, arkadaki worker'lar sırayla işler. Böylece sistem kilitlenmeden, paralel çalışır. Bizim veri modelimizdeki `outbox_jobs` tablosu zaten elle bunu yapmaya çalışıyordu — pgmq onun hazır, test edilmiş hali.

**Bilinmesi gereken tek kavram — visibility timeout (vt):** Bir worker mesajı kuyruktan "okuduğunda", o mesaj belli bir süre (örn. 30 sn) diğer worker'lara görünmez olur. Eğer worker işi bu sürede bitirip mesajı silerse sorun yok. Ama worker çökerse / iş yarıda kalırsa, süre dolunca mesaj tekrar görünür olur ve başka bir worker onu alıp işler. **Yani hiçbir iş kaybolmaz.** Kafka kurmadan bu güvenceyi elde etmiş oluyoruz.

```sql
CREATE EXTENSION pgmq;
SELECT pgmq.create('crawl_jobs');                          -- kuyruk oluştur
SELECT pgmq.send('crawl_jobs', '{"candidate_id": 42}');    -- işi bırak (producer)
SELECT * FROM pgmq.read('crawl_jobs', vt => 30, qty => 1); -- işi al, 30sn kilitle (consumer)
SELECT pgmq.delete('crawl_jobs', msg_id);                  -- iş bitti, sil
```

Postgres 14–18 ile çalışır, Docker imajıyla kurulu gelir. İleride gerçekten Kafka gerekirse, aynı producer/consumer mantığı Kafka'ya taşınabilir — yani şimdi pgmq kullanmak ileriyi tıkamaz.

---

## 2. bytea — Dosya Depolama

**Nedir?** Postgres'in binary (ikili) veri saklayan kolon tipidir. HTML çıktısı, ekran görüntüsü, logo, favicon gibi dosyaları doğrudan veritabanı tablosunda tutmanı sağlar. Bir extension bile değil — Postgres'in **içinde hazır** gelen bir özellik. Bizim için MinIO/S3'ün yerini tutuyor.

**Hangi sorunu çözüyor?** Crawler şüpheli siteyi gezdiğinde evidence (kanıt) dosyaları üretir. Bunları ayrı bir dosya sunucusu (MinIO/S3) kurmadan, aynı veritabanında, kayıtla aynı yerde tutmak istiyoruz.

**Nasıl çalışıyor — TOAST:** Postgres veriyi 8 KB'lık "sayfalara" böler; normalde büyük dosyalar buna sığmaz. **TOAST** denen mekanizma, büyük veriyi otomatik olarak arka planda parçalayıp ayrı bir alanda saklar ve gerektiğinde geri birleştirir. Bunu sen görmezsin, kendiliğinden olur. Normal SQL ile yazıp okursun, satırı silince dosya da silinir. **Sınır: kolon başına 1 GB** — bizim dosyalarımız için fazlasıyla yeterli.

```sql
CREATE TABLE evidence_blobs (id SERIAL PRIMARY KEY, file_type TEXT, data BYTEA);
ALTER TABLE evidence_blobs ALTER COLUMN data SET STORAGE EXTERNAL;
```
> İpucu: PNG, zip gibi *zaten sıkıştırılmış* dosyalar için `STORAGE EXTERNAL` ayarını yap. Yoksa Postgres onları tekrar sıkıştırmaya çalışır, boşuna CPU harcar.

**Alternatif — pg_largeobject:** Dosya 1 GB'ı geçecekse veya stream halinde (parça parça) okuman gerekiyorsa bu kullanılır, 4 TB'a kadar destekler. Ama daha karmaşık (ayrı API, OID takibi). MVP'de gerekmez.

> **Dürüst not:** Dosyayı veritabanında tutmak, dosya sistemine göre okumada ~10 kat daha yavaştır ve yedek (backup) boyutunu şişirir. MVP ve demo için tamamen sorun değil; ama ileride dosya hacmi büyüyünce S3/MinIO'ya geçmek gerekebilir. Bu yüzden veri modelinde dosyanın kendisi yerine *referansını* (`storage_ref`) tutma mantığını koruyoruz — geçiş kolay olsun diye.

---

## 3. tsvector — İçerik Araması

**Nedir?** Postgres'in metin arama (full-text search) motorudur. Bir metni alıp kelimelerine ayırır, arama yapılabilir hale getirir. Elasticsearch'ün temel işi olan "şu kelimeyi içeren kayıtları bul"u, ayrı bir arama sunucusu kurmadan Postgres içinde yapar. Bu da **hazır gelir**, 2008'den beri var.

**Hangi sorunu çözüyor?** Sakladığımız sayfa metni, başlık, marka adı, OCR çıktısı içinde "akbank", "login" gibi kelimeleri hızlı aramak istiyoruz. Düz `LIKE '%akbank%'` aramaları yavaş ve kabadır; tsvector kelime köklerini anlayarak (ör. "giriş" → "girişi", "girişler") çok daha akıllı ve hızlı arar.

**Nasıl çalışıyor:** Metin `to_tsvector` ile "lexeme"lere (kelime köklerine) çevrilir, gereksiz kelimeler (ve, bir, the) atılır. Sonra **GIN indeksi** kurulur — bu, kelimeden kayda giden bir "ters indeks"tir, tıpkı kitabın arkasındaki dizin gibi. Elasticsearch de aynı mantığı kullanır.

```sql
ALTER TABLE page_features ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title,''))) STORED;
CREATE INDEX page_search_idx ON page_features USING GIN(search_vector);
SELECT * FROM page_features WHERE search_vector @@ to_tsquery('akbank & login');
```

**Eğer gerçekten "Elasticsearch kalitesi" istenirse → pg_search (ParadeDB):** Bu bir extension'dır ve tsvector'un yetmediği yerde devreye girer. **BM25** denen, Elasticsearch'ün de kullandığı gelişmiş alaka sıralama algoritmasını, ayrıca typo toleransını (yanlış yazımları yakalama) ve cümle aramasını getirir. İndeks Postgres içinde durur ve yazma anında kendini günceller — Elasticsearch gibi ayrı bir sunucu ve senkronizasyon derdi yoktur. Sıralamada tsvector'dan ~20 kat hızlıdır.

**Karar:** MVP için **tsvector + GIN yeterli** (hazır, bedava, ekstra kurulum yok). "Elasticsearch kalitesi arama" gerçek bir gereksinim olursa, `pg_search`'e tek bir extension ekleyerek, veri modelini hiç bozmadan geçilir.

---

## Özet Tablo

| İhtiyaç | Klasik çözüm (kurmuyoruz) | Postgres karşılığı | Ne yapar | MVP kararı |
|---|---|---|---|---|
| İşleri sıraya dizmek | Kafka, AWS SQS | **pgmq** | İş kuyruğu, paralel işleme | ✅ pgmq |
| Dosya saklamak | MinIO, S3 | **bytea + TOAST** | Dosyayı DB'de tutar (≤1 GB) | ✅ bytea |
| Çok büyük dosya (>1 GB) | S3 | **pg_largeobject** | 4 TB'a kadar, stream | gerekirse |
| İçerikte kelime aramak | Elasticsearch | **tsvector + GIN** | Hızlı metin araması | ✅ tsvector |
| Gelişmiş arama (BM25, typo) | Elasticsearch | **pg_search** | "ES kalitesi" sıralama | ileride |

## Ana Mesaj
Tek bir PostgreSQL aynı anda dört rolü üstleniyor: **veritabanı + iş kuyruğu + dosya deposu + arama motoru.** Böylece MVP'de dört ayrı sistem kurup yönetmek yerine tek bir bloğu ayağa kaldırıyoruz. Ve her parça, ileride büyüdüğünde gerçek muadiline (Kafka, S3, Elasticsearch) geçişe izin verecek şekilde tasarlandığı için, bugünü çözerken yarını tıkamıyoruz.
