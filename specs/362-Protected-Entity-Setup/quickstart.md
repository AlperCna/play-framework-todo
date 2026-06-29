# Quickstart — Protected Entity Setup (362)

End-to-end validation that the feature works. Implementation details live in `tasks.md` / the code; this is a run & verify guide.

## Prerequisites

1. `.env` present (copy from `.env.example`): `DB_HOST=localhost`, `DB_PORT=55432`, `DB_NAME=mona_drp`, `DB_USER=mona`, `DB_PASSWORD=…`.
2. PostgreSQL up: `scripts/setup.ps1` (or `setup.sh`) → `ghcr.io/pgmq/pg18-pgmq` on 55432.
3. Migrations applied **by a human** (the agent never runs them): `scripts/migrate_drp_up.*` — applies `V001..V006` **and the new `V007`** (`entities.name` UNIQUE). Confirm `\d entities` shows `uq_entities_name`.

## Build & run

1. `build.sbt` now resolves `slick-pg` + `org.postgresql:postgresql` (first build downloads them).
2. `sbt run` → app on `http://localhost:9000`. (The `drp` datasource is lazy; the todo `default`/SQL-Server datasource is untouched and unused by these screens.)

## Manual acceptance walk-through (maps to spec SC-001…SC-008)

1. **Entity (US1)** — open `/drp/entities` → *New* → name `Akbank`, type `brand` → save. Appears in the (paginated) list. Reopen it. *(SC-001)*
2. **Duplicate entity (FR-002/SC-002)** — *New* → name `Akbank` again → rejected with a duplicate error; list unchanged. *(uniqueness enforced by `V007` index.)*
3. **Asset (US2)** — in the entity view → *Add asset* → type `domain`, value `akbank.com`, fill homepage/login/logo/favicon reference fields → save. Shows under the entity, active, no reference DOM summary.
4. **Duplicate asset (FR-004/SC-002)** — add another asset type `domain`, value `akbank.com` under the same entity → rejected (`(entity, asset_type, value)` active-unique).
5. **Asset group (US4)** — *Add group* `Akbank Direkt` → assign the asset to it → link saved. Add an asset with **no** group → also valid. Try assigning to a group from a *different* entity → prevented *(FR-005)*.
6. **Exclusion (US3)** — *Add exclusion* value `akbankdirekt.com`, match_type `exact`, reason `owned_unmonitored` → saved with `created_by="system"`, stored verbatim, not evaluated. *(SC-006/SC-008)*
7. **Edit (cross-cutting)** — edit any record → visible fields + `updated_at` change; `created_at` unchanged. *(SC-004)*
8. **No delete (FR-012/SC-005)** — confirm no delete control anywhere.
9. **Invalid parent (FR-009/SC-003)** — attempt asset/group/exclusion create with a non-existent entity id → error, nothing written.
10. **Required fields (FR-010)** — blank entity name / asset value / exclusion value → inline error, no partial row.
11. **Open enum (FR-013)** — an unrecognized `entity.type` / `exclusion.reason` round-trips as raw; `asset_type` / `match_type` outside the CHECK set are rejected.
12. **Read seam (FR-014/SC-006)** — `AssetReadPort.activeExclusions(entityId)` returns exactly that entity's active exclusions (verify via the entity view's exclusion list or a direct internal read); no matching applied.

## Automated tests (no live DB)

- `sbt test` → all suites green. New `test/drp/asset/`:
  - domain specs: smart-constructor validation (blank name/value rejected; enum codecs round-trip).
  - application specs via **in-memory repos**: create persists + lists; duplicate (entity/name, asset tuple, exclusion tuple) rejected with nothing written; invalid parent entity rejected; cross-entity group assignment prevented; edit moves `updated_at` only; `activeExclusions` returns the right set.
- Note: DB-enforced guarantees (unique indexes, `set_updated_at` trigger) are exercised by the manual walk-through above, not the in-memory suites (integration tests deferred — see plan Constitution Check IV/Dev-Workflow).
