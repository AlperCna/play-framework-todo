# Açık / Netleşmemiş Kararlar — Mona DRP

> CLAUDE.md §11 "Netleşen Kararlar"ın **tersi**: henüz **kesinleşmemiş** teknik kararlar burada durur.
> Kesin olmayan bir şeyi CLAUDE.md veya constitution'a "kuralmış gibi" yazmak yerine, açık soruyu burada
> **görünür** tutarız. Bir karar netleşince → ilgili dosyaya (CLAUDE.md / constitution) taşınır ve buradan
> silinir.
>
> Her madde aynı şablonda: **Soru** · **Kesin olan** · **Açık olan** · **Etki** · **Durum**.

---

## 1. Worker yürütme mekanizması (runtime)

- **Soru:** Pipeline worker'ları (crawler, feature extraction, similarity, risk scoring) hangi runtime
  ile *yürütülecek*?
- **Kesin olan:** Mesajlaşma `JobQueue` arayüzü + PGMQ arkasındadır (Constitution — queue soyutlaması;
  CLAUDE.md §6). Worker'lar **idempotent** (Constitution IV). İş kodu PGMQ'ya doğrudan bağlanmaz.
- **Açık olan:** Mesajı tüketip işi *çalıştıran* mekanizma — düz `Future` consumer mı, Akka (Typed)
  actor mı, Play scheduler mı? (todo iskelesi Akka Typed `CompletedTaskCleaner` kullanıyor; DRP için
  seçim yapılmadı.)
- **Etki:** `JobQueue` arayüzü bu seçimi soyutladığı için **iş mantığı seçimden bağımsız**; karar bir
  yürütme/ölçeklenme detayıdır, mimari sınırı etkilemez.
- **Durum:** açık.
