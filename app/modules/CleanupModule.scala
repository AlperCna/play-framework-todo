package modules

import com.google.inject.AbstractModule

import actors.CompletedTaskCleanerScheduler

/**
 * Zamanlanmis bakim isleri (background job) modulu.
 *
 * `asEagerSingleton()`: Guice'i bu sinifi acilista HEMEN olusturmaya zorlar — DEV
 * modunda bile (orada sade `@Singleton` baglamalari tembeldir/lazy, ama eager
 * singleton her iki Guice stage'inde de eager'dir). Olusturma aninda
 * [[actors.CompletedTaskCleanerScheduler]] constructor'i actor'u spawn eder ve
 * gece temizligi planlanir.
 *
 * application.conf'ta `play.modules.enabled += "modules.CleanupModule"` ile yuklenir.
 */
class CleanupModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[CompletedTaskCleanerScheduler]).asEagerSingleton()
}
