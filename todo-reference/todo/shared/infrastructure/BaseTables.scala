package todo.shared.infrastructure

import slick.jdbc.JdbcProfile

trait BaseTables {

  protected val profile: JdbcProfile
  import profile.api._

  abstract class BaseTable[R](tag: Tag, name: String) extends Table[R](tag, name) {
    def id: Rep[Long]
    def isDeleted: Rep[Boolean]
  }
}
