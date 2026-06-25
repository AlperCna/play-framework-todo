package todo.task.application

import java.time.{Duration => JDuration, LocalDateTime, LocalTime}
import javax.inject.{Inject, Singleton}

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import play.api.inject.ApplicationLifecycle

@Singleton
class CompletedTaskCleanerScheduler @Inject() (
    system: ActorSystem,
    taskService: TaskItemService,
    lifecycle: ApplicationLifecycle
) {

  private val RunHour = 1

  val ref: ActorRef[CompletedTaskCleaner.Command] =
    system.spawn(CompletedTaskCleaner(taskService), "completed-task-cleaner")

  private val schedule =
    system.scheduler.scheduleAtFixedRate(durationUntilNextRun(), 24.hours)(
      () => ref ! CompletedTaskCleaner.RunNow
    )(system.dispatcher)

  lifecycle.addStopHook { () =>
    schedule.cancel()
    Future.successful(())
  }

  private def durationUntilNextRun(): FiniteDuration = {
    val now      = LocalDateTime.now()
    val todayRun = now.toLocalDate.atTime(LocalTime.of(RunHour, 0))
    val nextRun  = if (todayRun.isAfter(now)) todayRun else todayRun.plusDays(1)
    JDuration.between(now, nextRun).toMillis.millis
  }
}
