# Research — Protected Entity Setup (362)

Phase 0 decisions. Format: **Decision · Rationale · Alternatives rejected.**

## R1 — Tables already exist (V001); no table-creating migration here

- **Decision**: Do NOT write migrations to create `entities`, `asset_groups`, `assets`, `exclusions` — `app/migrations/drp-postgres/V001__asset_layer_up.sql` already creates all four (with FKs `ON DELETE RESTRICT`, `created_at`/`updated_at` + `set_updated_at()` triggers, the indexes, and `CHECK` on `asset_type`/`match_type`). This feature builds the Scala/Slick layer on top.
- **Rationale**: V001 is the realized v5 baseline; re-creating tables would duplicate/contradict it.
- **Alternatives rejected**: New table migration (conflicts with V001); editing V001 (applied migrations are immutable).

## R2 — `entities.name` uniqueness → new migration `V007`

- **Decision**: Add `V007__entity_name_unique_up.sql` = `CREATE UNIQUE INDEX uq_entities_name ON entities(name);` (+ `_down` dropping it). The agent writes the file but does NOT execute it; a human applies it (`scripts/migrate_drp_*`).
- **Rationale**: US FR-002 requires globally-unique entity name (explicit acceptance criterion). The v5 final schema (`docs/migration_final_schema` §6.1/§8) omits it, so V001 has no such constraint. Constitution V requires a **race-safe authoritative guard** — a DB unique index — with the service pre-check as UX only.
- **Alternatives rejected**: App-layer-only uniqueness (not race-safe, violates V); skipping the requirement (contradicts the US).
- ⚠ This **extends** the documented v5 baseline → logged in plan.md Complexity Tracking; surfaced at the plan gate.

## R3 — Assets uniqueness reconciled to the authoritative schema

- **Decision**: Asset duplicate detection uses the existing `uq_assets_active_entity_type_value` = unique `(entity_id, asset_type, value) WHERE is_active=true`. The service does a pre-check on the same tuple; the partial unique index is the authoritative guard.
- **Rationale**: Spec FR-004 originally said `(entity_id, value)`; `V001` + schema §8 say `(entity_id, asset_type, value)` partial-on-active. CLAUDE.md §4: on conflict the schema wins. Since this feature never deactivates, partial == effectively full; the US duplicate example (same entity + value + `domain`) still rejects.
- **Alternatives rejected**: Adding a stricter `(entity_id, value)` unique (conflicts with the schema; would forbid the schema-legal "same value as both domain and subdomain"). FR-004 is reconciled in the spec.

## R4 — PostgreSQL via slick-pg + a dedicated `drp` datasource

- **Decision**: Add `slick-pg` (Slick-3.4 line, ~0.21.x) and `org.postgresql:postgresql` (42.7.x) to `build.sbt`. Define `drp.shared.infrastructure.MonaPgProfile` extending slick-pg's `ExPostgresProfile` with JSON(B) support. Add a `slick.dbs.drp` block in `application.conf` pointing at `MonaPgProfile$`, reading host/port/db/user/password from environment (`DB_*` in `.env`). Leave `slick.dbs.default` (SQL Server / todo) untouched. Slick adapters inject the **named** `drp` database config.
- **Rationale**: JSONB `assets.metadata` needs slick-pg; play-slick supports multiple named datasources; the constitution mandates PostgreSQL + slick-pg. The `.env` already defines `DB_HOST=localhost`, `DB_PORT=55432`, `DB_NAME=mona_drp`, `DB_USER=mona`, `DB_PASSWORD`.
- **Alternatives rejected**: Plain Slick PostgresProfile (no JSONB column support); reusing `slick.dbs.default` (that is SQL Server, todo's); storing metadata as TEXT (loses JSONB typing the schema chose).
- **Verify on first build**: exact slick-pg ↔ Slick 3.4.1 version pin.

## R5 — DRP does NOT build on `app/todo`; `drp.shared` re-creates the patterns

- **Decision**: Create a self-contained `app/drp/shared/` providing `ServiceResult`, `DomainError`, `Page`/`PageRequest`, `Clock`, `MonaPgProfile`, a base Twirl `main` layout, and the root `DrpModule`. The asset module mirrors the todo patterns (smart-constructor domain → `Either[DomainError,_]`, repository port + Slick adapter, per-module Guice module, `*FormData`/`*ViewModel`) but imports **nothing** from `todo.*`.
- **Rationale**: Constitution I forbids building DRP on the (to-be-removed) `app/todo` scaffold; "reuse established mechanisms" means reuse the *patterns*, recreated under `drp`.
- **Alternatives rejected**: Importing `todo.shared.*` (couples DRP to a scaffold slated for deletion — Constitution I violation); a generic cross-cutting shared lib spanning todo+drp (premature; out of scope).

## R6 — Open enums (TEXT) with a code-side codec; no DB ENUM

- **Decision**: `entity.type` and `exclusion.reason` are **open** enums — no DB CHECK (matches V001/schema); modeled as a sealed set of known values **plus** a raw fallback, with a String codec (recognized values typed, unrecognized tolerated as raw — FR-013). `asset.asset_type` and `exclusion.match_type` ARE DB-`CHECK`-constrained in V001, so the codec rejects unknowns for those before insert (or relies on the CHECK) — modeled as closed sets but still String-coded.
- **Rationale**: Schema decision (no PostgreSQL ENUM; TEXT + optional CHECK); FR-013 requires string round-trip with open-enum tolerance.
- **Alternatives rejected**: PostgreSQL ENUM (forbidden by constitution); bare `String` in domain (loses illegal-states-unrepresentable).

## R7 — Metadata captured as discrete reference fields → assembled to JSONB

- **Decision**: `assets.metadata` is modeled as a typed `AssetMetadata(homepageUrl, loginPageUrl, logoRef, faviconRef)` (all optional references). The web form has discrete fields; the system assembles the JSONB via `AssetMetadataCodec`. No raw-JSON entry by the analyst (spec Clarification Q3).
- **Rationale**: Structurally prevents malformed/binary/oversized content (FR-016); no DB CHECK needed (references-only is a code convention).
- **Alternatives rejected**: Free-form JSON textarea (parse + size + binary risk); separate columns per reference (diverges from the JSONB schema).

## R8 — Testing: in-memory adapter per port + ScalaTest

- **Decision**: Each of the four repository ports ships an `InMemory*Repository` test adapter; domain smart-constructors and application services are covered by ScalaTest against those (no live DB). The PostgreSQL/Slick path + the V007 constraint are validated manually via `quickstart.md`.
- **Rationale**: Constitution Dev-Workflow (suites stay green; in-memory enables DB-less service tests; each new port ships an in-memory adapter). DB-enforced invariants (unique indexes, triggers) are out of in-memory scope → manual quickstart, integration tests later.
- **Alternatives rejected**: Live-DB-only tests (slow, infra-bound); skipping in-memory adapters (violates Dev-Workflow).

## R9 — No delete; `is_active` not user-managed; `created_by="system"`; no auth gate

- **Decision**: Controllers expose only create/edit/list/view. `is_active` defaults true and has no UI toggle. `exclusions.created_by` is the constant `"system"` (DB default already does this; the app does not pass a user). Screens have no authentication gate this feature.
- **Rationale**: US §2/§5/§6 + spec Clarification Q1. Auth/current-user deferred; reusing todo's `AuthenticatedAction` would violate Constitution I.
- **Alternatives rejected**: Soft-delete/deactivate UI (out of scope); wiring current-user/auth (out of scope + todo coupling).
