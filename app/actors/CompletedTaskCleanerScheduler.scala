package actors

import java.time.{Duration => JDuration, LocalDateTime, LocalTime}
import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import play.api.inject.ApplicationLifecycle

import services.TaskItemService

/**
 * [[CompletedTaskCleaner]] actor'unu acilista baslatan VE her gece `RunHour`:00'da ona
 * `RunNow` yollayan zamanlayici. ("NE ZAMAN" sorumlulugu burada; actor sade kalir.)
 *
 * Akka'nin hazir `system.scheduler`'ini kullaniriz: ilk calismaya kadarki sureyi
 * (bir sonraki 01:00) hesaplar, sonra 24 saatte bir tetikler. Self-rescheduling timer'a
 * gore daha basit; karsiliginda zamanlama duvar-saatine "her seferinde" yeniden
 * sabitlenmez (24 saatlik sabit aralik, DST gununde +/-1 saat kayabilir) — ogrenme/
 * tek-instance uygulama icin kabul edilebilir bir denge.
 *
 * Bu sinif [[modules.CleanupModule]]'de `asEagerSingleton()` ile baglanir; boylece
 * hicbir yere enjekte edilmese bile acilista olusturulur. Actor'u Play'in classic
 * `ActorSystem`'i uzerinde `spawn` ile uretiriz (`...adapter._` `.spawn`'i ekler).
 */
@Singleton
class CompletedTaskCleanerScheduler @Inject() (
    system: ActorSystem,
    taskService: TaskItemService,
    lifecycle: ApplicationLifecycle
) {

  /** Temizligin calisacagi yerel saat (00-23). */
  private val RunHour = 1

  /** "Ne yapilacak"i bilen actor (yalnizca RunNow'a tepki verir). Manuel tetik icin public. */
  val ref: ActorRef[CompletedTaskCleaner.Command] =
    system.spawn(CompletedTaskCleaner(taskService), "completed-task-cleaner")

  // "Ne zaman":
  // - scheduleAtFixedRate(...)	= Bu saate tekrarlı bir görev kaydeder. "Bir kez kur, kendisi tekrar tekrar tetikler."
  //    - 1. argüman: durationUntilNextRun() =	İlk tetik ne zaman → bir sonraki 01:00'a kalan süre.
  //    - 2. argüman: 24.hours = 	Hangi sıklıkla → ilk tetikten sonra tekrar aralığı.
  //    - 3. arguman: () => ref ! RunNow (Runnable)	= Her tetikte ne çalışsın → actor'e RunNow mesajı yolla. Zamanlamanın "iş" tarafına dokunduğu tek nokta.
  private val schedule =
    system.scheduler.scheduleAtFixedRate(durationUntilNextRun(), 24.hours)(
      () => ref ! CompletedTaskCleaner.RunNow
    )(system.dispatcher)

  // ! Operatoru
  // - Fire-and-forget: yanıt beklemez, Unit döner. Mesajı kutuya bırakır, gönderen bloklanmaz, akış devam eder.
  // - Asenkron: actor o mesajı sonra, kendi thread'inde, sırası gelince işler. Gönderen ile alıcı zamanı ayrışır.

  // Uygulama kapanirken (veya dev-reload) zamanlayiciyi iptal et — kaynak sizintisini onler.
  lifecycle.addStopHook { () =>
    schedule.cancel()
    Future.successful(())
  }

  /** Su andan bir sonraki `RunHour`:00'a kalan sure (altyapi kodu; zaman uretimi serbest). */
  private def durationUntilNextRun(): FiniteDuration = {
    val now      = LocalDateTime.now()
    val todayRun = now.toLocalDate.atTime(LocalTime.of(RunHour, 0))
    val nextRun  = if (todayRun.isAfter(now)) todayRun else todayRun.plusDays(1)
    JDuration.between(now, nextRun).toMillis.millis
  }
}
