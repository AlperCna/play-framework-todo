package repositories

import domain.task.TaskItemCategory

/**
 * TaskItem <-> Category iliski (join) verilerine erisim sozlesmesi (port).
 * Sorgular varsayilan olarak silinmemis (aktif) iliskileri doner.
 */
trait TaskItemCategoryRepository {

  /** Bir gorevin aktif (silinmemis) iliskileri. */
  def listByTask(taskItemId: Long): Seq[TaskItemCategory]

  /** Bir gorev-kategori cifti icin aktif iliski; yoksa None. */
  def findActiveLink(taskItemId: Long, categoryId: Long): Option[TaskItemCategory]

  /** Yeni iliski ekler; atanmis id ile doner. */
  def add(link: TaskItemCategory): TaskItemCategory

  /** Var olan iliskiyi gunceller (orn. soft-delete sonrasi); yoksa None. */
  def update(link: TaskItemCategory): Option[TaskItemCategory]
}
