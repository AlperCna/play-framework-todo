import com.google.inject.AbstractModule

import repositories.{InMemoryTodoRepository, TodoRepository}

/**
 * Uygulamanin bagimlilik enjeksiyonu (DI) yapilandirmasi.
 *
 * Play, `conf/application.conf` icinde aksi belirtilmedikce kok paketteki
 * `Module` sinifini otomatik olarak yukler.
 *
 * Burada `TodoRepository` arayuzunu somut `InMemoryTodoRepository`
 * implementasyonuna bagliyoruz. Veritabanina gectiginde sadece bu satiri
 * yeni implementasyonu gosterecek sekilde degistirmen yeterli.
 */
class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TodoRepository]).to(classOf[InMemoryTodoRepository])
  }
}
