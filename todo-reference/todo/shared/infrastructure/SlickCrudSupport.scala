package todo.shared.infrastructure

import scala.concurrent.{ExecutionContext, Future}

import play.api.db.slick.HasDatabaseConfigProvider
import slick.jdbc.JdbcProfile

import todo.shared.application.{Page, PageRequest}

trait SlickCrudSupport extends HasDatabaseConfigProvider[JdbcProfile] with Tables {

  import profile.api._

  protected def listActive[A, R, T <: BaseTable[R]](q: Query[T, R, Seq])(
      implicit m: RowMapper[A, R],
      ec: ExecutionContext
  ): Future[Seq[A]] =
    db.run(q.filter(!_.isDeleted).sortBy(_.id).result).map(_.map(m.toDomain))

  protected def findOneActive[A, R, T <: BaseTable[R]](q: Query[T, R, Seq])(
      implicit m: RowMapper[A, R],
      ec: ExecutionContext
  ): Future[Option[A]] =
    db.run(q.filter(!_.isDeleted).result.headOption).map(_.map(m.toDomain))

  protected def pageActive[A, R, T <: BaseTable[R]](q: Query[T, R, Seq], page: PageRequest)(
      implicit m: RowMapper[A, R],
      ec: ExecutionContext
  ): Future[Page[A]] = {
    val active = q.filter(!_.isDeleted)
    val window = active.sortBy(_.id).drop(page.offset).take(page.limit.toLong)
    val totalF = db.run(active.length.result)
    val rowsF  = db.run(window.result)
    for {
      total <- totalF
      rows  <- rowsF
    } yield Page.from(rows.map(m.toDomain), page, total.toLong)
  }

  protected def insertReturningId[R, T <: BaseTable[R]](q: TableQuery[T], row: R)(
      implicit ec: ExecutionContext
  ): Future[Long] =
    db.run((q returning q.map(_.id)) += row)
}
