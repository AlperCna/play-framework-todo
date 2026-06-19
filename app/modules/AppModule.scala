package modules

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment, Mode}

import context.{CategoryModule, TaskModule, UserModule}
import persistence.{InMemoryPersistenceModule, SlickPersistenceModule}
import services.{Clock, SystemClock}

/**
 * Uygulamanin DOMAIN bagimlilik enjeksiyonu (DI) kok modulu.
 *
 * `application.conf`'ta `play.modules.enabled += "modules.AppModule"` ile yuklenir
 * (isim-konvansiyonuyla auto-load yerine ACIK kayit; boylece tum top-level
 * moduller tek yerde — config'de — gorunur: AppModule = domain, SecurityModule =
 * auth, CleanupModule = job'lar). Play `(environment, configuration)`'i ctor'a
 * enjekte eder.
 *
 * Bu modul yalnizca UC is yapar:
 *   1. Domain-genelinde gecerli baglamalar (orn. `Clock`).
 *   2. BACKEND SECIMI: ortama gore tek bir persistence modulunu `install` eder
 *      (altyapi-politikasi). Repo port->impl listeleri o modullerde durur.
 *   3. CONTEXT WIRING: her bounded-context kendi service modulunu kurar.
 *
 * Konvansiyon: `application.conf` = top-level concern'lerin REGISTRY'si; `install`
 * = bir concern'in IC kompozisyonu (AppModule, persistence + context'leri install
 * eder). Boylece "hangi backend" (politika) ile "hangi context'ler var" (domain
 * kaydi) ayrilir ve buyuyen domain'de bu dosya kucuk kalir; yeni entity/context
 * eklemek mevcut satirlara dokunmaz (degisim izole; `persistence.db.Tables`
 * facade'iyle ayni felsefe).
 *
 * Backend yollari:
 *   - Test modu veya `app.inMemory = true`: bellek-ici repo'lar + seed'li
 *     `persistence.inmemory.InMemoryDatabase`. Testler gercek DB gerektirmez.
 *   - Dev/Prod: Slick (SQL Server) repo'lari; `Database`/`InMemoryDatabase` HIC
 *     kullanilmaz.
 *
 * Katmanlar arayuz->implementasyon olarak baglanir:
 *   Controller -> Service -> Repository -> (Slick | InMemory)
 */
class AppModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  override def configure(): Unit = {
    // 1) Domain-genelinde tekil baglamalar.
    bind(classOf[Clock]).to(classOf[SystemClock])

    // 2) Backend SECIMI — tek karar noktasi. Repo port->impl eslesmeleri
    //    secilen persistence modulunde (entity basina tek satir).
    val useInMemory =
      environment.mode == Mode.Test ||
        configuration.getOptional[Boolean]("app.inMemory").getOrElse(false)
    install(if (useInMemory) new InMemoryPersistenceModule else new SlickPersistenceModule)

    // 3) Bounded-context service wiring. Yeni context = burada tek `install`.
    install(new TaskModule)
    install(new CategoryModule)
    install(new UserModule)
  }
}
