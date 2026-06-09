package models

import java.time.LocalDate

import domain.common.Priority

/**
 * Form'dan gelen veriyi tasiyan yardimci model (DTO).
 *
 * Domain entity'sinden (`TaskItem`) ayri tutulur: kullanici `id`, audit, ya da
 * tamamlanma durumu girmez. `completed` BILEREK yoktur; tamamlama edit formundan
 * degil, listedeki Complete/Reopen butonlarindan (domain davranisi) yapilir.
 *
 * `priority` form katmaninda zaten `Priority`'ye cevrilmis gelir (controller'daki
 * ozel `Formatter[Priority]` sayesinde); boylece controller ham sayi tasimaz.
 */
case class TaskItemFormData(
    title: String,
    description: Option[String],
    priority: Priority,
    dueDate: Option[LocalDate]
)
