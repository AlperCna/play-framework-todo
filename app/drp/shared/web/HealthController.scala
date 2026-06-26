package drp.shared.web

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import slick.jdbc.JdbcProfile

@Singleton
class HealthController @Inject() (
    cc: ControllerComponents,
    dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig._
  import profile.api._

  def health(): Action[AnyContent] = Action.async {
    db.run(sql"SELECT 1".as[Int]).map { _ =>
      Ok("Mona DRP — OK | DB: connected")
    }.recover { case ex =>
      InternalServerError(s"Mona DRP — DB ERROR: ${ex.getMessage}")
    }
  }
}
