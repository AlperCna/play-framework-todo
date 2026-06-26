package todo.shared.web

import javax.inject.{Inject, Singleton}

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import todo.task.application.{CompletedTaskCleaner, CompletedTaskCleanerScheduler}

@Singleton
class CleanupController @Inject() (
    cc: MessagesControllerComponents,
    cleaner: CompletedTaskCleanerScheduler
) extends MessagesAbstractController(cc) {

  def runCleanup = Action { implicit request =>
    cleaner.ref ! CompletedTaskCleaner.RunNow
    Redirect(todo.task.web.routes.TaskItemController.list())
      .flashing("success" -> request.messages("task.cleanup.triggered"))
  }
}
