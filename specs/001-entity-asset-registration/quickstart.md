# Quickstart & Validation: Protected Entity & Asset Registration

A runnable guide proving the slice works end to end (form → PostgreSQL → server-rendered page). Validates the spec's acceptance criteria. This is a validation/run guide — implementation lives in `tasks.md` / the implement phase.

## Prerequisites

- Docker Desktop running.
- `.env` present (copy from `.env.example` if missing).
- The asset-layer schema applied to the local PostgreSQL — **a manual step** (the app never auto-migrates):

```powershell
# One-time: start Postgres+PGMQ and apply V001..V006 (creates entities/assets among the 16 tables)
.\scripts\setup.ps1
# (or, if the container is already up:)  .\scripts\migrate_drp_up.ps1
```

Expected: migrations report `V001__asset_layer_up.sql ... OK` and `entities` / `assets` exist.

## Run

```powershell
sbt run
```

Open `http://localhost:9000/drp/assets`.

## Validation scenarios (map 1:1 to acceptance criteria)

| # | Action | Expected | Criterion |
|---|---|---|---|
| 1 | Register entity name "Akbank", type "brand" | Entity appears in the listing with id, name, type | FR-001, SC-001 |
| 2 | Under "Akbank", register asset value "akbank.com" | Asset shown under Akbank: type `domain`, active, value exactly "akbank.com" | FR-004, FR-007, SC-001, SC-005 |
| 3 | Register an asset with metadata reference fields only (homepage/login/logo/favicon/reference DOM summary) | Accepted and stored | FR-006 |
| 4 | Submit entity with blank name | Visible validation message; nothing persisted | FR-002, SC-002 |
| 5 | Submit an asset under a non-existent entity id | Rejected; nothing persisted | FR-005, SC-002 |
| 6 | Register entity "Akbank" again (same name) | Accepted — duplicate entity names allowed | Clarification 2026-06-26 |
| 7 | Register "akbank.com" under Akbank a second time (still active) | Rejected with a visible message; no duplicate row | FR-008, SC-006 |
| 8 | Register entity type "institution" | Accepted; type shown as entered | FR-003, SC-004 |
| 9 | View an entity that has no assets | Empty asset list, no error | FR-009, SC-003 |
| 10 | Register asset value "not a domain" (non-blank, no dot) | Accepted, stored as-is | Clarification 2026-06-26 |

## Automated checks

```powershell
sbt test
```

Expected: new `drp.asset` ScalaTest specs pass (domain validation + services via in-memory repositories) **and** all pre-existing todo suites still pass (Constitution: tests stay green).

## Done / sign-off

- [ ] Scenarios 1–10 pass by manual observation on the screen.
- [ ] `sbt test` green (new + existing).
- [ ] No rows persisted on any rejection path (spot-check `entities` / `assets` after scenarios 4, 5, 7).
- [ ] `slick.dbs.default` (todo / SQL Server) untouched; DRP uses `slick.dbs.drp`.
