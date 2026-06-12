package persistence.inmemory

import domain.category.Category
import domain.task.{TaskItem, TaskItemCategory}
import domain.user.User

/**
 * Bellek-ici "veritabani" soyutlamasi (interface).
 *
 * Db gibi davranan TEK bir singleton ([[InMemoryDatabase]]) tum tablolari tutar
 * ve bellek-ici repository'ler bu arayuz uzerinden erisir. Gercek DB yolu (Slick)
 * bu arayuzu KULLANMAZ; o repolar dogrudan veritabanina konusur.
 */
trait Database {
  def users: InMemoryTable[User]
  def tasks: InMemoryTable[TaskItem]
  def categories: InMemoryTable[Category]
  def taskCategories: InMemoryTable[TaskItemCategory]
}
