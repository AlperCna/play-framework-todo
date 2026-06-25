# Todo Uygulamasının Modüler Monolite Dönüşümü

Bu belge, play-framework-todo projesinin orijinal "teknik katman" yapısından
"modüler monolit" yapısına nasıl dönüştürüldüğünü, neden dönüştürüldüğünü ve
hâlâ çözülmeyi bekleyen yapışma noktalarını açıklar.

DRP modüllerinin nasıl yapılandırılacağını anlamak için bu belge referans alınmalıdır.

---

## 1. Başlangıç Durumu — Teknik Katman Yapısı

Orijinal yapıda klasörler iş alanına (domain) göre değil, teknik role göre ayrılmıştı:

```
app/
├── domain/
│   ├── task/       ← TaskItem, Urgency, TaskItemCategory
│   ├── category/   ← Category
│   ├── user/       ← User
│   └── common/     ← AuditInfo, DomainError, Priority
├── services/       ← TaskItemService, CategoryService, UserService (hepsi bir arada)
├── repositories/
│   ├── interfaces/ ← tüm repository trait'leri
│   ├── sql/        ← tüm Slick implementasyonları
│   └── inmemory/   ← tüm in-memory implementasyonları
├── persistence/db/ ← tüm Slick tablo tanımları
├── controllers/    ← tüm HTTP controller'ları
├── modules/        ← tüm Guice modülleri
├── actors/
├── forms/
└── views/
```

### Sorun

Bu yapıda **task**, **category** ve **user** iş alanları fiziksel olarak birbirine karışmış durumda.
`services/` klasörüne bakan biri hangi servisin hangi domain'e ait olduğunu dosya adından çıkarmak zorunda.
Daha tehlikelisi, modüller arası bağımlılıklar derleyici tarafından görünmez — hiçbir şey
`TaskItemService`'in `CategoryRepository`'yi doğrudan içe aktarmasını engelleyemez.

---

## 2. Hedef — Modüler Monolit Yapısı

Temel prensip:

```
Modül önce, teknik katman sonra.
```

Her iş alanı kendi paketinde toplanır. Katmanlar (domain / application / infrastructure / web)
modülün **içinde** yer alır, modüller arasında değil.

```
app/todo/
├── task/
│   ├── domain/           ← TaskItem, Urgency, TaskItemCategory
│   ├── application/      ← TaskItemService, TaskItemRepository, TaskItemCategoryRepository,
│   │                        TaskModule, CompletedTaskCleaner, CompletedTaskCleanerScheduler
│   ├── infrastructure/   ← SlickTaskItemRepository, SlickTaskItemCategoryRepository,
│   │                        InMemoryTaskItemRepository, InMemoryTaskItemCategoryRepository,
│   │                        TasksTable, TaskItemCategoriesTable, TaskMappers
│   └── web/              ← TaskItemController, TaskItemFormData
│
├── category/
│   ├── domain/           ← Category
│   ├── application/      ← CategoryService, CategoryRepository, CategoryModule
│   ├── infrastructure/   ← SlickCategoryRepository, InMemoryCategoryRepository,
│   │                        CategoriesTable, CategoryMappers
│   └── web/              ← CategoryController, CategoryFormData
│
├── user/
│   ├── domain/           ← User, AuthenticationResult
│   ├── application/      ← UserService, UserRepository, UserModule
│   ├── infrastructure/   ← SlickUserRepository, InMemoryUserRepository,
│   │                        UsersTable, UserMappers, DbUsernamePasswordAuthenticator,
│   │                        SecurityModule
│   └── web/              ← AuthController, LoginFormData, RegisterFormData
│
├── shared/
│   ├── domain/           ← AuditableEntity, AuditInfo, DomainError, Priority
│   ├── application/      ← CrudRepository, Clock, ServiceResult, Page, PageRequest, PageWindow
│   ├── infrastructure/   ← BaseTables, Tables (facade), RowMapper, SlickCrudSupport,
│   │                        SlickPersistenceModule, InMemoryPersistenceModule,
│   │                        Database, InMemoryDatabase, InMemoryTable, AuditMapper
│   └── web/              ← AuthenticatedAction, AuthenticatedRequest,
│                            HomeController, CleanupController, ForTryController
│
└── boot/
    └── AppModule.scala   ← Tüm modülleri birleştiren tek giriş noktası
```

### AppModule — Modüllerin Birleşme Noktası

```scala
class AppModule(environment: Environment, configuration: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Clock]).to(classOf[SystemClock])
    install(if (useInMemory) new InMemoryPersistenceModule else new SlickPersistenceModule)
    install(new TaskModule)
    install(new CategoryModule)
    install(new UserModule)
  }
}
```

Her modülün kendi Guice `Module`'ü vardır. `AppModule` bunları compose eder;
modüller birbirini doğrudan import etmez.

---

## 3. Dönüşümün Getirdikleri

| Önce | Sonra |
|---|---|
| `services/TaskItemServiceImpl.scala` | `todo/task/application/TaskItemServiceImpl.scala` |
| `repositories/interfaces/CategoryRepository.scala` | `todo/category/application/CategoryRepository.scala` |
| `persistence/db/UsersTable.scala` | `todo/user/infrastructure/UsersTable.scala` |
| `controllers/TaskItemController.scala` | `todo/task/web/TaskItemController.scala` |
| `modules/context/TaskModule.scala` | `todo/task/application/TaskModule.scala` |

**Kazanım**: Bir dosyayı açmadan önce hangi modüle ait olduğu paket adından okunabilir.
`todo.task.application.TaskItemService` → task modülü, application katmanı.

---

## 4. Kalan Yapışma Noktaları

Dönüşüm yapısal organizasyonu çözdü; ancak modüller arası 4 bağımlılık hâlâ mevcut.

### 4.1 TaskItem → Category Doğrudan İmport (domain katmanı ihlali)

**Dosya**: [`app/todo/task/domain/TaskItem.scala:5`](../../app/todo/task/domain/TaskItem.scala)

```scala
import todo.category.domain.Category  // ← task domain'i category domain'ini içe aktarıyor
```

`TaskItem.assignToCategory(category: Category, ...)` metodu `Category` nesnesini
doğrudan parametre olarak alıyor. Bu, task domain'ini category domain'ine bağlıyor.

**Neden sorun?** Domain sınıfları saf olmalı; başka modüllerin domain sınıflarını içe aktarmamalı.

**Çözüm yolu**: `category: Category` yerine `categoryId: Long` + `isDeleted: Boolean` alınabilir:
```scala
def assignToCategory(
  categoryId: Long,
  categoryIsDeleted: Boolean,  // caller doğrulayıp geçer
  existingLinks: Seq[TaskItemCategory],
  now: Instant,
  by: String
): Either[DomainError, Option[TaskItemCategory]]
```
Bu değişiklik `todo.category.domain.Category` import'unu task domain'inden tamamen kaldırır.

---

### 4.2 TaskItemServiceImpl → CategoryRepository Doğrudan Enjeksiyon (application katmanı ihlali)

**Dosya**: [`app/todo/task/application/TaskItemServiceImpl.scala:8`](../../app/todo/task/application/TaskItemServiceImpl.scala)

```scala
import todo.category.application.CategoryRepository  // ← task, category application'ını import ediyor

class TaskItemServiceImpl @Inject() (
  taskRepo: TaskItemRepository,
  categoryRepo: CategoryRepository,  // ← cross-module DI
  ...
)
```

`TaskItemServiceImpl`, category modülünün repository'sine doğrudan erişiyor.
Bu, iki modül arasında derleme zamanı bağımlılığı oluşturuyor.

**Çözüm yolu**: `task/application/ports/CategoryQuery.scala` adında bir port tanımlanır,
`CategoryRepository`'nin task'ın ihtiyaç duyduğu minimal arayüzünü içerir:

```scala
// todo/task/application/ports/CategoryQuery.scala
package todo.task.application.ports

import scala.concurrent.Future

trait CategoryQuery {
  def exists(categoryId: Long): Future[Boolean]
  def isDeleted(categoryId: Long): Future[Boolean]
}
```

`TaskItemServiceImpl` artık `CategoryRepository` değil `CategoryQuery` alır.
Category modülü bu trait'i implement eden bir adapter sağlar.
Böylece task → category bağımlılığı tersine çevrilmiş olur (Dependency Inversion).

---

### 4.3 Tables.scala — Tüm Tabloları Birleştiren Facade (infrastructure katmanı)

**Dosya**: [`app/todo/shared/infrastructure/Tables.scala`](../../app/todo/shared/infrastructure/Tables.scala)

```scala
trait Tables
    extends UsersTable
    with CategoriesTable
    with TasksTable
    with TaskItemCategoriesTable
```

Slick'in çalışması için tüm tablo tanımlarının tek bir profile üzerinden erişilebilir olması gerekir.
Bu nedenle `Tables` facade'ı tüm modüllerin tablolarını bir araya toplar.

**Bu bir sorun mu?** Kısmen. Facade'ın `shared/infrastructure/` içinde olması
ve her modülün kendi tablo tanımını kendi `infrastructure/` paketinde tutması kabul edilebilir.
Ancak yeni bir tablo eklendiğinde `Tables.scala`'ya da dokunmak gerekiyor — bu gizli bir bağımlılık.

**Çözüm yolu** (MVP sonrası): Her modül kendi `SlickProfile` mixin'ini sağlar;
`Tables` sadece persistence modülü seçilirken otomatik compose edilir.
MVP için mevcut yapı yeterlidir.

---

### 4.4 task_item_categories — Cross-Module FK (veritabanı katmanı)

Veritabanında `task_item_categories` tablosu hem `tasks` hem `categories` tablosuna FK ile bağlı.
Bu, şema seviyesinde cross-module bağımlılık.

**Sahiplik kararı**: `task_item_categories` tablosu **task modülüne** aittir.
- Tablo tanımı: `todo/task/infrastructure/TaskItemCategoriesTable.scala`
- Repository: `todo/task/application/TaskItemCategoryRepository.scala`
- Category modülü bu tabloya doğrudan erişmez; ihtiyacı olursa task modülünün portu üzerinden okur.

**DRP için ders**: Cross-module FK'larda sahiplik açıkça belirlenmeli.
Tablonun sahibi olan modül yazar; diğerleri port üzerinden okur.

---

## 5. Özet Tablo

| Yapışma Noktası | Konum | Durum | Çözüm |
|---|---|---|---|
| `TaskItem` → `Category` import | `task/domain/TaskItem.scala:5` | ⚠️ Mevcut | `categoryId: Long` parametresine geç |
| `TaskItemServiceImpl` → `CategoryRepository` | `task/application/TaskItemServiceImpl.scala:8` | ⚠️ Mevcut | `CategoryQuery` port'u tanımla |
| `Tables.scala` tek facade | `shared/infrastructure/Tables.scala` | ⚠️ Kabul edilebilir | MVP sonrası refactor |
| `task_item_categories` cross-module FK | DB şema | ✅ Sahiplik belirlendi | Task modülü sahip; category okumaz |

---

## 6. DRP Modülleri İçin Çıkarılan Dersler

Bu deneyimden DRP'ye taşınan kurallar:

1. **Domain sınıfları saf kalır** — başka modüllerin domain sınıflarını içe aktarmaz.
   Sadece primitive (`Long`, `String`, `Boolean`) veya `shared/domain` sınıflarına bağımlı olabilir.

2. **Cross-module erişim port üzerinden** — bir modülün başka bir modülün verisine ihtiyacı
   varsa hedef modül `application/ports/` altında bir trait tanımlar; kaynak modül bunu implement eder.

3. **Single Writer Principle** — her tablo yalnızca bir modül tarafından yazılır.
   Cross-module FK olan tablolarda sahiplik açıkça belirlenir.

4. **Guice modülleri modül sınırını korur** — `TaskModule` yalnızca task bağımlılıklarını bağlar.
   `AppModule` bunları compose eder; modüller birbirini import etmez.

5. **Shared sadece gerçekten paylaşılan şeyler içerir** — `AuditInfo`, `Clock`, `Page`, `ServiceResult`
   birden fazla modülün kullandığı kavramlardır. Domain'e özgü şeyler shared'e girmez.
