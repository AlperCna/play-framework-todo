package actions

import play.api.i18n.MessagesApi
import play.api.mvc.{MessagesRequest, Request}

import domain.user.User

/**
 * Giris yapmis kullaniciyi (current user) tasiyan istek sarmalayicisi.
 *
 * `MessagesRequest`'i GENISLETIR; boylece controller/sablonlardaki
 * `request.messages(...)` ve form helper'larinin istedigi ortulu
 * `MessagesRequestHeader` aynen calismaya devam eder. Ek olarak `user` alani
 * sunar — .NET'teki `CurrentUser`'in karsiligi.
 */
final class AuthenticatedRequest[A](val user: User, request: Request[A], messagesApi: MessagesApi)
    extends MessagesRequest[A](request, messagesApi)
