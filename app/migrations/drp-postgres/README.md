# Mona DRP PostgreSQL Manual Migrations

This directory contains manually executed PostgreSQL migrations for Mona DRP.

Play Evolutions, Flyway, or any automatic migration runner is not used here.
Run the files manually in the order below against the target PostgreSQL database.

## Scope

These files create the Mona DRP PostgreSQL target schema only.

The existing Todo SQL Server bootstrap file at `app/migrations/initialize.sql` is kept separate. It is not converted or executed by this DRP migration set.

## Preflight

Before running the migrations, confirm:

- The target database is PostgreSQL.
- The database user can create tables, indexes, triggers, functions, and extensions.
- `pgmq` is available in the target PostgreSQL environment before running `V006__pgmq_queues_up.sql`.
- The migration is being run against the intended database, not a production database by accident.
- Seed/demo data is not expected from these migrations.

Useful checks:

```sql
SELECT current_database(), current_user, version();
SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name = 'pgmq';
```

## Up Order

```text
1. V001__asset_layer_up.sql
2. V002__discovery_layer_up.sql
3. V003__storage_layer_up.sql
4. V004__pipeline_layer_up.sql
5. V005__decision_layer_up.sql
6. V006__pgmq_queues_up.sql
```

Example:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V001__asset_layer_up.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V002__discovery_layer_up.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V003__storage_layer_up.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V004__pipeline_layer_up.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V005__decision_layer_up.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V006__pgmq_queues_up.sql
```

## Down Order

```text
1. V006__pgmq_queues_down.sql
2. V005__decision_layer_down.sql
3. V004__pipeline_layer_down.sql
4. V003__storage_layer_down.sql
5. V002__discovery_layer_down.sql
6. V001__asset_layer_down.sql
```

Example:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V006__pgmq_queues_down.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V005__decision_layer_down.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V004__pipeline_layer_down.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V003__storage_layer_down.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V002__discovery_layer_down.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f app/migrations/drp-postgres/V001__asset_layer_down.sql
```

`V006__pgmq_queues_down.sql` drops the Mona DRP queues, but intentionally does not drop the `pgmq` extension. The extension may be shared by other schemas or applications in the same database.

## Version Tracking

There is no automatic migration runner and no DB-side migration ledger yet.

Until a `schema_migrations` style ledger is introduced, the operator must record which files were applied, when, and against which database. A future migration can add an explicit ledger table if manual tracking becomes too error-prone.

## Rules

- `V001` through `V005` own their own `BEGIN` / `COMMIT` transaction.
- `V006` does not use an explicit transaction block because it manages the `pgmq` extension and PGMQ queue DDL.
- `CREATE INDEX CONCURRENTLY` is not used because these files run inside transactions.
- Every physical foreign key uses `ON DELETE RESTRICT`.
- PostgreSQL enum types are not used. Critical lifecycle fields use `TEXT` with `CHECK`.
- Large HTML, DOM, screenshot, OCR, and binary content must go through `blob_storage`.
- JSONB/TEXT size guardrail CHECK constraints are intentionally not included in MVP migrations. Real data growth will be observed first; hard limits can be added later in a dedicated hardening migration.
- PGMQ setup is isolated in `V006` so the core schema can be installed without PGMQ.
- Seed/demo data is intentionally not included.
- `assets.value` and `exclusions.value` normalization is an application service responsibility in MVP. No `lower(value)` expression unique index is created yet.
- Down migrations are intended for development, test, and controlled rollback scenarios. Always take a backup before running them against persistent environments.
