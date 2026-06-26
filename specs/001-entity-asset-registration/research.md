# Phase 0 Research: Protected Entity & Asset Registration

All open technical questions resolved before design. No `NEEDS CLARIFICATION` remain (spec clarifications already settled the functional ones; the items below are technical/infrastructure decisions for the modular monolith).

---

## D1 — DRP gets a dedicated PostgreSQL Slick connection (`slick.dbs.drp`)

- **Decision**: Add a second Slick database `slick.dbs.drp` in `conf/application.conf`, pointing at the local Docker PostgreSQL via `.env` values (host/port/db/user/password, port 55432). DRP repositories inject it with `@play.db.NamedDatabase("drp")` (play-slick `HasDatabaseConfigProvider` qualified to `drp`). Leave `slick.dbs.default` (todo scaffold's SQL Server) **unchanged**.
- **Rationale**: The todo scaffold still runs on SQL Server through `default`; repointing `default` would break it. A named DB keeps the two stacks isolated and matches the modular-monolith "single app, clear boundaries" intent. `.env` is already the DRP connection source (CLAUDE.md §2, `docs/drp-local-setup.md`).
- **Alternatives considered**: (a) Repoint `default` to PostgreSQL — rejected: breaks the todo baseline before it is removed. (b) Single shared DB — rejected: todo and DRP target different engines today.

## D2 — Add `slick-pg` + PostgreSQL driver; define `MonaPgProfile`

- **Decision**: Add to `build.sbt`: `org.postgresql % postgresql` and `com.github.tminglei %% slick-pg` (+ the play-json sub-artifact for JSONB). Define `app/drp/shared/infrastructure/MonaPgProfile.scala` extending `ExPostgresProfile` with Play-JSON JSONB support, exposing a `MonaPgProfile.api`. DRP tables import this profile (not the plain `JdbcProfile`).
- **Rationale**: `assets.metadata` is JSONB; vanilla Slick has no JSONB column type. `slick-pg` is the standard Play/Slick JSONB bridge and is already the documented target (CLAUDE.md "slick-pg ⬜"). Play-JSON aligns with Play's bundled JSON.
- **Alternatives considered**: Map `metadata` as `String`/`text` and parse manually — rejected: loses JSONB typing and round-trips, fragile. doobie/another stack — rejected: project standard is Slick.

## D3 — Do NOT reuse the todo scaffold's `BaseTables` / `SlickCrudSupport`

- **Decision**: The asset module defines its own minimal Slick tables and repository implementations. It mirrors the *patterns* (repository port + Slick adapter, `RowMapper`-style Row↔domain mapping, `ServiceResult`/`Either[DomainError, _]`, per-module Guice module, in-memory test adapter) but does not extend `todo.shared.infrastructure.BaseTables` / `SlickCrudSupport`.
- **Rationale**: Those traits assume (a) an `isDeleted` soft-delete column on every table and (b) the `default` DB config via `HasDatabaseConfigProvider[JdbcProfile]`. DRP `entities`/`assets` have **no** `is_deleted` (assets use `is_active`; deletes are out of scope), and DRP uses the `drp` PostgreSQL DB. Reusing them would force a fake column and the wrong DB. Constitution I also forbids building DRP on the todo scaffold.
- **Alternatives considered**: Generalize the todo base traits to be soft-delete-agnostic and DB-agnostic — rejected for this slice: larger refactor of scaffold code that is slated for removal; out of scope.

## D4 — `asset_type` as a sealed ADT + codec; `entities.type` as free-text `String`

- **Decision**: `AssetType` is a sealed trait with `Domain` / `Subdomain` cases plus a single string codec (`asValue`/`fromValue`), matching the DB `CHECK (asset_type IN ('domain','subdomain'))`. `entities.type` is stored as a plain non-empty `String` (no fixed set), per the v5 decision that `entities.type` has **no** CHECK.
- **Rationale**: Mirrors the constitution's "TEXT + CHECK on lifecycle fields, code-side sealed/open enum + codec" rule. `asset_type` is constrained at the DB; `entities.type` is intentionally open (brand/person/institution/…).
- **Alternatives considered**: PG enum — forbidden by constitution. Free-text `asset_type` — rejected: the DB CHECK constrains it to domain/subdomain.

## D5 — `assets.metadata` JSONB ↔ references-only value object

- **Decision**: Domain holds an `AssetMetadata` value object with optional reference fields only (`homepageUrl`, `loginPageUrl`, `logoRef`, `faviconRef`, `referenceDomSummary`). The Slick column maps JSONB ↔ Play `JsValue` via `MonaPgProfile`; the mapper encodes/decodes `AssetMetadata` ↔ JSON. Default is `{}`. The encoder MUST reject/omit any non-reference (binary/base64) content (FR-006).
- **Rationale**: Keeps big content out of JSONB (Constitution V, CLAUDE.md), keeps the domain typed and testable.
- **Alternatives considered**: Store metadata as opaque `JsValue` in the domain — rejected: leaks JSON into domain (impurity) and weakens validation.

## D6 — `updated_at` is DB-trigger-maintained; `created_at` DB-defaulted

- **Decision**: Inserts omit `updated_at`/`created_at` (DB `DEFAULT now()` + the `set_updated_at` trigger from V001 handle them). Row mappers read both as read-only timestamps. The app never writes `updated_at`.
- **Rationale**: V001 installs `set_updated_at()` BEFORE UPDATE triggers on mutable tables; application code setting `updated_at` would be redundant/inconsistent (migration_final_schema §2.6 / §11).
- **Alternatives considered**: App-managed timestamps — rejected: contradicts the trigger design.

## D7 — Listing avoids N+1 (bulk fetch + in-memory group)

- **Decision**: The listing reads **all** entities and **all** assets with two queries, then groups assets by `entity_id` in memory to render "per entity, its assets".
- **Rationale**: Constitution V forbids DB calls inside loops; a query-per-entity would violate it. At demo scale this is trivially cheap.
- **Alternatives considered**: One query per entity for its assets — rejected (N+1). A SQL join returning entity×asset rows — acceptable but the two-query group is simpler and avoids row duplication for entities with many assets.

## D8 — Testing: ScalaTest + in-memory repository adapters

- **Decision**: Domain validation and application services are covered by ScalaTest specs using `InMemoryEntityRepository` / `InMemoryAssetRepository` (no live DB). The PostgreSQL/Slick path is validated manually via `quickstart.md`. Existing todo suites must keep passing.
- **Rationale**: Matches the constitution's Dev-Workflow rule (ScalaTest suites must keep passing; in-memory backend enables DB-less service tests) and the todo scaffold's existing in-memory pattern.
- **Alternatives considered**: Testcontainers/integration tests against PostgreSQL — deferred (the v1 rigor is human review + quickstart; integration tests can come later).

---

### Inputs that needed NO research (already fixed by spec/constitution)

- Stack (Scala/Play/Twirl/PostgreSQL/Guice), no-React, manual migrations, module layering, references-only metadata, ON DELETE RESTRICT, no PG enum — all imposed decisions in the spec's Constraints and the constitution.
- Duplicate policy and asset-value validation — settled in the spec's Clarifications (Session 2026-06-26).
