package todo.shared.web

import play.api.i18n.MessagesApi
import play.api.mvc.{MessagesRequest, Request}

import todo.user.domain.User

final class AuthenticatedRequest[A](val user: User, request: Request[A], messagesApi: MessagesApi)
    extends MessagesRequest[A](request, messagesApi)
