package persistence.db

/**
 * Tum tablo fragment'lerini birlestiren CEPHE (facade).
 *
 * `SlickCrudSupport` ve Slick repo'lari tek `with Tables` ile butun tablolari
 * (+ ortak `BaseTable`, [[BaseTables]]'tan) alir.
 *
 * YENI TABLO EKLEMEK: yeni bir `*Table` fragment'i (tablo + Row) yaz, buraya
 * `with XTable` ekle. Bu dosya hep kucuk kalir; mevcut fragment'lere dokunulmaz
 * (merge-conflict ekseni entity basina izole). 50 tabloda bile burasi ~10 satir.
 *
 * DOMAIN != DB: her `*Row` duz satirdir; zengin domain'e donusum
 * `persistence.db.mappers` altindaki [[RowMapper]] instance'larinda yapilir.
 */
trait Tables
    extends UsersTable
    with CategoriesTable
    with TasksTable
    with TaskItemCategoriesTable
