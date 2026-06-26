package todo.shared.application

import scala.concurrent.Future

trait CrudRepository[A] {
  def list(): Future[Seq[A]]
  def get(id: Long): Future[Option[A]]
  def add(entity: A): Future[A]
  def update(entity: A): Future[Option[A]]
}
