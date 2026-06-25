package todo.shared.web

import play.api.mvc.{MessagesAbstractController, MessagesControllerComponents}

import javax.inject.{Inject, Singleton}

@Singleton
class ForTryController @Inject()(cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {
  def try1 = TODO

  def try2 = Action {
    Ok("Try 2")
  }
}
