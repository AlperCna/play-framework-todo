-- =====================================================================
-- play-framework-todo  |  Microsoft SQL Server (T-SQL) sema scripti
-- =====================================================================
-- Domain modeli != DB modeli: domain'deki `Urgency` ADT'si burada
--   priority_value (INT) + due_date (DATE NULL) olarak DUZLESIR.
-- `High => due_date zorunlu` invariant'i hem domain'de (Urgency.High) hem
--   de burada CK_tasks_high_requires_due CHECK'i ile garanti edilir.
--
-- Bu script MANUEL calistirilir (Play Evolutions kullanilmaz).
-- Once veritabanini olustur ve sec:
--   CREATE DATABASE todo; GO
--   USE todo; GO
-- =====================================================================

-- ============ USERS ============
CREATE TABLE users (
  id          BIGINT IDENTITY(1,1) PRIMARY KEY,
  email       NVARCHAR(255) NOT NULL,
  password    NVARCHAR(255) NOT NULL,         -- duz metin (domain'de hash yok)
  created_at  DATETIME2     NOT NULL CONSTRAINT DF_users_created_at DEFAULT SYSUTCDATETIME(),
  created_by  NVARCHAR(255) NOT NULL,
  updated_at  DATETIME2     NULL,
  updated_by  NVARCHAR(255) NOT NULL CONSTRAINT DF_users_updated_by DEFAULT '',
  is_deleted  BIT           NOT NULL CONSTRAINT DF_users_is_deleted DEFAULT 0,
  deleted_at  DATETIME2     NULL,
  deleted_by  NVARCHAR(255) NOT NULL CONSTRAINT DF_users_deleted_by DEFAULT ''
);
GO
-- aktif (silinmemis) e-postada tekillik: filtered unique index
CREATE UNIQUE INDEX UX_users_email_active ON users(email) WHERE is_deleted = 0;
GO

-- ============ CATEGORIES ============
CREATE TABLE categories (
  id          BIGINT IDENTITY(1,1) PRIMARY KEY,
  name        NVARCHAR(255) NOT NULL,
  description NVARCHAR(MAX) NOT NULL,          -- Category.description zorunlu (Option degil)
  user_id     BIGINT NOT NULL,
  created_at  DATETIME2 NOT NULL CONSTRAINT DF_categories_created_at DEFAULT SYSUTCDATETIME(),
  created_by  NVARCHAR(255) NOT NULL,
  updated_at  DATETIME2 NULL,
  updated_by  NVARCHAR(255) NOT NULL CONSTRAINT DF_categories_updated_by DEFAULT '',
  is_deleted  BIT NOT NULL CONSTRAINT DF_categories_is_deleted DEFAULT 0,
  deleted_at  DATETIME2 NULL,
  deleted_by  NVARCHAR(255) NOT NULL CONSTRAINT DF_categories_deleted_by DEFAULT '',
  CONSTRAINT FK_categories_user FOREIGN KEY (user_id) REFERENCES users(id)
);
GO
CREATE INDEX IX_categories_user_active ON categories(user_id) WHERE is_deleted = 0;
GO

-- ============ TASKS ============
CREATE TABLE tasks (
  id             BIGINT IDENTITY(1,1) PRIMARY KEY,
  title          NVARCHAR(255) NOT NULL,
  description    NVARCHAR(MAX) NULL,           -- TaskItem.description Option
  priority_value INT  NOT NULL,               -- 0=Low, 1=Medium, 2=High (urgency.priority.value)
  due_date       DATE NULL,                    -- urgency.dueDate
  is_completed   BIT  NOT NULL CONSTRAINT DF_tasks_is_completed DEFAULT 0,
  completed_at   DATETIME2 NULL,
  user_id        BIGINT NULL,                  -- soft-delete'te NULL'a cekilir (softDeleteWithUser)
  created_at     DATETIME2 NOT NULL CONSTRAINT DF_tasks_created_at DEFAULT SYSUTCDATETIME(),
  created_by     NVARCHAR(255) NOT NULL,
  updated_at     DATETIME2 NULL,
  updated_by     NVARCHAR(255) NOT NULL CONSTRAINT DF_tasks_updated_by DEFAULT '',
  is_deleted     BIT NOT NULL CONSTRAINT DF_tasks_is_deleted DEFAULT 0,
  deleted_at     DATETIME2 NULL,
  deleted_by     NVARCHAR(255) NOT NULL CONSTRAINT DF_tasks_deleted_by DEFAULT '',
  CONSTRAINT FK_tasks_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT CK_tasks_priority CHECK (priority_value IN (0, 1, 2)),
  -- ASIL INVARIANT: High(2) ise due_date zorunlu -- Urgency.High ile birebir
  CONSTRAINT CK_tasks_high_requires_due CHECK (priority_value <> 2 OR due_date IS NOT NULL)
);
GO
CREATE INDEX IX_tasks_user_active ON tasks(user_id) WHERE is_deleted = 0;
GO

-- ============ TASK_ITEM_CATEGORIES (N-N) ============
CREATE TABLE task_item_categories (
  id            BIGINT IDENTITY(1,1) PRIMARY KEY,
  task_item_id  BIGINT NOT NULL,
  category_id   BIGINT NOT NULL,
  created_at    DATETIME2 NOT NULL CONSTRAINT DF_tic_created_at DEFAULT SYSUTCDATETIME(),
  created_by    NVARCHAR(255) NOT NULL,
  updated_at    DATETIME2 NULL,
  updated_by    NVARCHAR(255) NOT NULL CONSTRAINT DF_tic_updated_by DEFAULT '',
  is_deleted    BIT NOT NULL CONSTRAINT DF_tic_is_deleted DEFAULT 0,
  deleted_at    DATETIME2 NULL,
  deleted_by    NVARCHAR(255) NOT NULL CONSTRAINT DF_tic_deleted_by DEFAULT '',
  CONSTRAINT FK_tic_task     FOREIGN KEY (task_item_id) REFERENCES tasks(id),
  CONSTRAINT FK_tic_category FOREIGN KEY (category_id)  REFERENCES categories(id)
);
GO
-- ayni (task, category) icin tek aktif link
CREATE UNIQUE INDEX UX_tic_active ON task_item_categories(task_item_id, category_id) WHERE is_deleted = 0;
GO

-- =====================================================================
-- (Opsiyonel) SEED -- mevcut InMemoryDatabase demo verisiyle ayni.
-- Temiz bir DB'de calistir; IDENTITY id'lerinin 1'den basladigi varsayilir.
-- =====================================================================
INSERT INTO users (email, password, created_by)
  VALUES (N'demo@example.com', N'demo123', N'system');
GO
INSERT INTO categories (name, description, user_id, created_by)
  VALUES (N'Is', N'Ise dair gorevler', 1, N'system');
GO
INSERT INTO tasks (title, priority_value, due_date, user_id, created_by)
  VALUES (N'Play framework ogren', 1, NULL, 1, N'system');
GO
INSERT INTO tasks (title, description, priority_value, due_date, user_id, created_by)
  VALUES (N'Domain modeli tasarla', N'Rich domain model + repository + service', 2,
          DATEADD(day, 7, CAST(SYSUTCDATETIME() AS DATE)), 1, N'system');
GO
INSERT INTO task_item_categories (task_item_id, category_id, created_by)
  VALUES (1, 1, N'system');
GO
