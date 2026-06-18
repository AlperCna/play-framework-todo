package actors

import scala.util.{Failure, Success}

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import services.TaskItemService

/**
 * Tamamlanmis gorevleri soft-delete eden bakim actor'u (Akka Typed).
 *
 * SORUMLULUK SINIRI: actor yalnizca "RunNow geldiginde isi yap"i bilir. "NE YAPILACAK"
 * [[services.TaskItemService.purgeCompleted]]'te; "NE ZAMAN" (zamanlama) ise
 * [[CompletedTaskCleanerScheduler]]'dadir (Akka scheduler). Boylece actor sade, yeniden
 * kullanilabilir ve test edilebilir bir "istek uzerine calisan" isci olarak kalir.
 *
 * OGRENME NOTLARI (Akka Typed):
 *   - Mesaj protokolu `Command` ADT'siyle TIPLENIR: actor yalnizca bu mesajlari alir;
 *     baska bir tip gondermek DERLENMEZ (classic `Any` yerine tip guvenligi).
 *   - `purgeCompleted()` bir `Future` doner. Actor icinde ASLA `Await` etmeyiz ve
 *     Future'in callback'inden actor state'ine DOKUNMAYIZ (thread guvenligini bozar).
 *     Sonucu `ctx.pipeToSelf` ile bir mesaja (CleanupDone/CleanupFailed) cevirip
 *     kendimize yollariz; boylece is yine tek-threadli mesaj dongusunde islenir.
 */
object CompletedTaskCleaner {

  /** Actor'un kabul ettigi mesajlar. */
  sealed trait Command

  /**
   * Temizligi tetikler. Hem zamanlayici ([[CompletedTaskCleanerScheduler]]) hem de
   * manuel/admin tetikleme (controllers.AdminController) ayni mesaji yollar; PUBLIC
   * olmasinin sebebi budur. Diger mesajlar dahilidir (yalnizca actor kendine yollar).
   */
  case object RunNow extends Command

  private final case class CleanupDone(deleted: Int) extends Command
  private final case class CleanupFailed(error: Throwable) extends Command

  def apply(taskService: TaskItemService): Behavior[Command] =
    // setup yalnizca `ctx`'i (log, pipeToSelf) yakalamak icin; artik baslangic isi yok.
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case RunNow =>
          // Future'i BEKLEME; sonucu mesaja cevirip kendi kutuna yolla (tek-thread guvenligi).
          ctx.pipeToSelf(taskService.purgeCompleted()) {
            case Success(count) => CleanupDone(count)
            case Failure(ex)    => CleanupFailed(ex)
          }
          Behaviors.same

        case CleanupDone(deleted) =>
          ctx.log.info(s"[CompletedTaskCleaner] $deleted tamamlanmis gorev soft-delete edildi.")
          Behaviors.same

        case CleanupFailed(ex) =>
          ctx.log.error("[CompletedTaskCleaner] Temizlik basarisiz oldu.", ex)
          Behaviors.same
      }
    }
}
