package todo.shared.infrastructure

trait RowMapper[D, R] {
  def toRow(domain: D): R
  def toDomain(row: R): D
}

object RowMapper {
  def apply[D, R](implicit m: RowMapper[D, R]): RowMapper[D, R] = m
}
