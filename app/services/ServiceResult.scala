package services

import scala.concurrent.{ExecutionContext, Future}

import domain.common.DomainError

/**
 * `Future` ve `Either`'i birlikte tasiyan kucuk yardimci (mini "EitherT").
 *
 * Gercek (Slick) DB ile repo'lar `Future` doner; domain ise hala SAF `Either`
 * uretir. Bu ikisini cats gibi bir kutuphane EKLEMEDEN for-comprehension icinde
 * zincirleyebilmek icin `map`/`flatMap` saglar. Sol (Left/hata) deger gelince
 * zincir kisa devre yapar (sonraki adimlar calismaz).
 *
 * Servis metotlari icte `ServiceResult` ile zincirler, sonunda `.value` ile
 * `Future[Either[DomainError, A]]` doner.
 */
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

  /** Hazir bir degeri Right olarak sarar. */
  def pure[A](a: A): ServiceResult[A] = ServiceResult(Future.successful(Right(a)))

  /** Domain'in urettigi saf `Either`'i lift'ler. */
  def fromEither[A](e: Either[DomainError, A]): ServiceResult[A] = ServiceResult(Future.successful(e))

  /** Hata uretmeyen bir `Future`'i Right olarak lift'ler. */
  def fromFuture[A](fa: Future[A])(implicit ec: ExecutionContext): ServiceResult[A] =
    ServiceResult(fa.map(Right(_)))

  /** Repo'dan gelen `Future[Option[A]]`: bos ise `Left(err)`. */
  def fromOptionF[A](foa: Future[Option[A]], err: => DomainError)(
      implicit ec: ExecutionContext
  ): ServiceResult[A] =
    ServiceResult(foa.map(_.toRight(err)))
}
