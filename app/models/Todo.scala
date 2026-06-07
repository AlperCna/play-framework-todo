package models

/**
 * Bir yapilacak ogesini (todo) temsil eden entity.
 *
 * @param id        Benzersiz kimlik. Yeni olusturulurken repository tarafindan atanir.
 * @param title     Yapilacak isin basligi.
 * @param completed Isin tamamlanip tamamlanmadigi.
 */
case class Todo(id: Long, title: String, completed: Boolean)

/**
 * Form'dan gelen veriyi tasiyan yardimci model.
 *
 * `Todo`'dan ayri tutuyoruz cunku kullanici form doldururken `id` girmez;
 * id repository tarafindan uretilir. Boylece form sadece kullanicidan
 * beklenen alanlari icerir.
 */
case class TodoFormData(title: String, completed: Boolean)
