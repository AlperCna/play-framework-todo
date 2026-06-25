package todo.shared.application

import scala.concurrent.{ExecutionContext, Future}

import todo.shared.domain.DomainError

final case class ServiceResult[A](value: Future[Either[DomainError, A]]) {

  def map[B](f: A => B)(implicit ec: ExecutionContext): ServiceResult[B] =
    ServiceResult(value.map(_.map(f)))

  def flatMap[B](f: A => ServiceResult[B])(implicit ec: ExecutionContext): ServiceResult[B] =
    ServiceResult(value.flatMap {
      case Right(a) => f(a).value
      case Left(e)  => Future.successful(Left(e))
    })
}

object ServiceResult {

  def pure[A](a: A): ServiceResult[A] = ServiceResult(Future.successful(Right(a)))

  def fromEither[A](e: Either[DomainError, A]): ServiceResult[A] = ServiceResult(Future.successful(e))

  def fromFuture[A](fa: Future[A])(implicit ec: ExecutionContext): ServiceResult[A] =
    ServiceResult(fa.map(Right(_)))

  def fromOptionF[A](foa: Future[Option[A]], err: => DomainError)(
      implicit ec: ExecutionContext
  ): ServiceResult[A] =
    ServiceResult(foa.map(_.toRight(err)))
}
