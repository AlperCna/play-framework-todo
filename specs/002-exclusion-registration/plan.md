# Implementation Plan: Exclusion (Allowlist) Registration

**Branch**: `002-exclusion-registration` | **Date**: 2026-06-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-exclusion-registration/spec.md`

## Summary

Deliver the third foundational setup-surface slice: an analyst registers an **exclusion** (allowlist
entry — value + match type + reason) under an existing protected entity, then views that entity's
exclusions on a **dedicated, per-entity** server-rendered Twirl screen. Persistence targets the existing
PostgreSQL `exclusions` table (already created by `V001__asset_layer_up.sql`). Technical approach: add
exclusion files to the **existing** `app/drp/asset/` module (domain → application(+ports) →
infrastructure → web), reusing the already-wired `slick.dbs.drp` connection, `MonaPgProfile`, and the
existing `EntityRepository.existsById` for owning-entity validation. No migration, no new dependency,
and no change to the US-001 entity/asset behavior or its `/drp/assets` listing view.

## Technical Context

**Language/Version**: Scala 2.13.18

**Primary Dependencies**: Play Framework 2.9, play-slick 5.2, slick-pg 0.21.1 (+ `slick-pg_play-json`),
PostgreSQL JDBC 42.7.4, Guice, Twirl. **All already present in `build.sbt`** — this slice adds none.

**Storage**: PostgreSQL (local Docker, port 55432) — existing `exclusions` table from
`app/migrations/drp-postgres/V001__asset_layer_up.sql`. Accessed via the already-configured
`slick.dbs.drp` connection (`@NamedDatabase("drp")`, reads `.env`). `slick.dbs.default` (todo / SQL
Server) untouched.

**Testing**: ScalaTest + scalatestplus-play; service- and domain-level tests use in-memory repository
adapters (mirroring `InMemoryEntityRepository`), no live DB required. PostgreSQL path validated manually
via `quickstart.md`.

**Target Platform**: JVM server (Play), local Docker PostgreSQL for dev.

**Project Type**: Web application — server-rendered (Twirl), modular monolith.

**Performance Goals**: Foundational CRUD; no numeric target. The per-entity listing MUST read exclusions
in bulk (one query) and the duplicate pre-check MUST be one query — no DB call inside a loop
(Constitution V).

**Constraints**: Domain layer pure (no Play/Slick/HTTP/JSON/DB types); same-module reuse of the
`EntityRepository` port for owning-entity existence (no cross-module DI); single-writer (asset module
owns `exclusions`); `match_type` is a sealed ADT + codec backed by the DB CHECK, `reason` is open
non-blank text (no CHECK); `created_by` relies on the DB default `'system'` (no current-user seam);
`updated_at` is DB-trigger-maintained (not app-written); manual migrations only (app never
auto-migrates); the US-001 entity/asset behavior and its `/drp/assets` view MUST NOT be altered.

**Scale/Scope**: Small — a handful of exclusions per entity for the Akbank demo; single analyst persona;
no pagination, auth, edit/delete/deactivation, or global (`entity_id` null) exclusions in this slice.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Architecture Boundary Conformance | PASS | All logic in `app/drp/asset/`; pure `domain`; `web`→`application`→`domain`, `infrastructure`→`application/ports`. Owning-entity check reuses the **same-module** `EntityRepository` port (not a cross-module dependency). Single-writer: asset module owns `exclusions`. Reuses established mechanisms (smart ctor → `Either[DomainError,_]`, port + Slick adapter, per-module Guice module, in-memory test adapter). Does not build on / reshape `app/todo/`. |
| II. Scope Boundary | PASS | In-scope files enumerated below: new exclusion files + additive routes + one `AssetModule` binding. US-001 entity/asset files and the `/drp/assets` listing view are **not** modified (see Phase 0 D5 for the nav-link reconciliation). No pipeline/downstream tables touched. |
| III. Single Responsibility | PASS | Each service/repo method does one thing (register; list-by-entity; duplicate pre-check). |
| IV. Correctness Before Performance | PASS | Correctness-only slice; no performance work mixed in. |
| V. Data Access Discipline | PASS | List = one bulk query (by `entity_id`); duplicate pre-check = one query. No per-row DB calls. Race backstop: the V001 partial unique index `uq_exclusions_entity_active_value_match` is the authoritative guard; the service pre-check is UX only. No JSONB / large content (the `exclusions` table has none). |
| VI. Abstraction Reflected in Comments | PASS | Short WHAT comments on new traits/public methods; WHY only when non-obvious (CLAUDE.md §10). |
| VII. Type-Conditional Behavior Preservation | PASS | Type = Feature Implementation. The one neighboring behavior to preserve — US-001 entity/asset registration + the `/drp/assets` view — is explicitly left untouched. |
| Platform: Staging discipline | N/A | `exclusions` is upstream of the pipeline; this slice writes no `candidate_discoveries`/`candidates`. |
| Platform: Queue/Storage abstraction | N/A | No PGMQ/blob in this slice. |
| Platform: Explainable scoring | N/A | No scoring in this slice. |
| Platform: Schema invariants | PASS | Uses existing V001 schema as-is: no PG enum (`match_type` TEXT+CHECK + code-side sealed ADT; `reason` open TEXT + non-blank `String`), FK `entity_id → entities(id)` ON DELETE RESTRICT, no new FK, no score fields. `created_by` NOT NULL DEFAULT `'system'` honored via insert omission. |
| Platform: Migration discipline | PASS | No migration authored or executed; relies on already-written V001 (which created `exclusions`) applied manually. |
| Dev Workflow: tests pass | PASS | New ScalaTest specs added (domain + service via in-memory repos); existing suites keep passing. |

**Result: PASS — no violations. Complexity Tracking left empty.**

## Project Structure

### Documentation (this feature)

```text
specs/002-exclusion-registration/
├── plan.md              # This file
├── research.md          # Phase 0 — technical decisions
├── data-model.md        # Phase 1 — exclusion entity, fields, validation
├── quickstart.md        # Phase 1 — runnable validation guide
├── contracts/
│   └── exclusion-routes.md   # Phase 1 — route/form contract for the per-entity screen
└── checklists/
    └── requirements.md  # (from /speckit-specify)
```

### Source Code (repository root)

```text
app/drp/asset/                                  # EXISTING module — additive only
├── domain/
│   ├── Exclusion.scala                         # NEW: final case class + smart ctor; Either[AssetDomainError, _]
│   ├── ExclusionMatchType.scala                # NEW: sealed ADT + codec (exact|registrable_domain|subdomain_of|pattern)
│   └── AssetDomainError.scala                  # EDIT (additive): + BlankExclusionValue, BlankExclusionReason, InvalidMatchType, DuplicateActiveExclusion (reuses existing UnknownEntity)
├── application/
│   ├── ExclusionService.scala                  # NEW: register + listByEntity (trait)
│   ├── ExclusionServiceImpl.scala              # NEW: domain validate → entity-exists (EntityRepository) → duplicate pre-check → persist
│   ├── ports/
│   │   └── ExclusionRepository.scala           # NEW: create / listByEntity / existsActiveDuplicate
│   └── AssetModule.scala                       # EDIT (additive): bind ExclusionService + ExclusionRepository
├── infrastructure/
│   ├── ExclusionsTable.scala                   # NEW: Slick mapping to `exclusions` (+ ExclusionRow)
│   ├── SlickExclusionRepository.scala          # NEW: @NamedDatabase("drp")
│   └── InMemoryExclusionRepository.scala       # NEW: test adapter (enforces active-duplicate guard in memory)
└── web/
    ├── ExclusionController.scala               # NEW: GET per-entity list + POST create
    ├── ExclusionFormData.scala                 # NEW: value + matchType + reason (entityId from path)
    └── views/
        └── exclusions.scala.html               # NEW: dedicated per-entity exclusions screen + create form

# Root wiring (in scope — additive)
conf/routes                                     # + GET/POST /drp/assets/entities/:entityId/exclusions

# Tests
test/drp/asset/domain/ExclusionSpec.scala       # NEW: smart-ctor validation
test/drp/asset/application/ExclusionServiceSpec.scala  # NEW: service via in-memory repos

# NOT touched (already present / explicitly out of scope)
build.sbt                                        # slick-pg + postgres already present
conf/application.conf                            # slick.dbs.drp + AssetModule already enabled
app/migrations/drp-postgres/V001__asset_layer_up.sql   # exclusions table already created
app/drp/shared/infrastructure/MonaPgProfile.scala      # reused as-is
app/drp/asset/web/views/list.scala.html         # US-001 view — MUST stay untouched
app/drp/asset/{domain/Entity, application/Entity*, infrastructure/*Entity*, web/{Asset,Entity}*}  # US-001 — reused, not modified
```

**Structure Decision**: Single Play modular-monolith project. This feature is **additive within the
existing `app/drp/asset/` module** — exclusions belong to the module that already owns
`entities`/`assets`. It reuses the existing `slick.dbs.drp` connection, `MonaPgProfile`, and
`EntityRepository.existsById`; it adds no dependency and no migration. The only edits to existing files
are additive: new error cases in `AssetDomainError`, two new bindings in `AssetModule`, and two new
routes in `conf/routes`. The US-001 entity/asset code and the `/drp/assets` listing view are reused
unchanged.

## Complexity Tracking

> No Constitution Check violations. Nothing to justify.
