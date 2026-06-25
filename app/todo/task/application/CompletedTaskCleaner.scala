package todo.task.application

import scala.util.{Failure, Success}

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object CompletedTaskCleaner {

  sealed trait Command

  case object RunNow extends Command

  private final case class CleanupDone(deleted: Int) extends Command
  private final case class CleanupFailed(error: Throwable) extends Command

  def apply(taskService: TaskItemService): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case RunNow =>
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
