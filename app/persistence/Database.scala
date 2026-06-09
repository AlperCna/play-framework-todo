package persistence

import domain.category.Category
import domain.task.{TaskItem, TaskItemCategory}
import domain.user.User

/**
 * "Veritabani" soyutlamasi (interface).
 *
 * DOMAIN-SPEC ve proje notu geregi: simdilik gercek bir DB baglantisi yok.
 * Db gibi davranan TEK bir singleton ([[InMemoryDatabase]]) tum tablolari tutar
 * ve repository'ler bu arayuz uzerinden erisir. Ilerde Slick/H2 gibi gercek bir
 * DB'ye gecmek icin yalnizca bu arayuzun implementasyonunu degistirmek yeter;
 * repository ve ust katmanlar etkilenmez.
 */
trait Database {
  def users: Table[User]
  def tasks: Table[TaskItem]
  def categories: Table[Category]
  def taskCategories: Table[TaskItemCategory]
}
